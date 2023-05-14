/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

@Slf4j
@RequiredArgsConstructor
public class InMemoryMessageStore implements GatewayClient, TrackingClient {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicLong nextIndex = new AtomicLong();
    private final Map<String, TrackerRead> trackers = new ConcurrentHashMap<>();

    private final ConcurrentSkipListMap<Long, SerializedMessage> messageLog = new ConcurrentSkipListMap<>();
    private final Map<String, Long> consumerTokens = new ConcurrentHashMap<>();

    @Getter
    private final MessageType messageType;
    private final Duration messageExpiration;

    public InMemoryMessageStore(MessageType messageType) {
        this(messageType, Duration.ofMinutes(2));
    }

    @Override
    public Awaitable send(Guarantee guarantee, SerializedMessage... messages) {
        synchronized (this) {
            Arrays.stream(messages).forEach(m -> {
                if (m.getIndex() == null) {
                    m.setIndex(nextIndex.updateAndGet(IndexUtils::nextIndex));
                }
                messageLog.put(m.getIndex(), m);
            });
            if (messageExpiration != null) {
                purgeExpiredMessages(messageExpiration);
            }
            this.notifyAll();
        }
        return Awaitable.ready();
    }

    protected void purgeExpiredMessages(Duration messageExpiration) {
        var threshold = FluxCapacitor.currentTime().minus(messageExpiration).toEpochMilli();
        messageLog.headMap(IndexUtils.indexFromMillis(threshold)).clear();
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId,
                                                Long lastIndex, ConsumerConfiguration configuration) {
        return read(new SimpleTrackerRead(consumer, trackerId, lastIndex, configuration, messageType));
    }

    public CompletableFuture<int[]> claimSegment(TrackerRead trackerRead) {
        if (trackerRead.getMessageType() != MessageType.RESULT && !Objects.equals(
                trackerRead.getTrackerId(), trackers.computeIfAbsent(
                        trackerRead.getConsumer(), c -> trackerRead).getTrackerId())) {
            return CompletableFuture.supplyAsync(
                    () -> new int[]{0, 0},
                    CompletableFuture.delayedExecutor(trackerRead.getDeadline() - currentTimeMillis(), MILLISECONDS));
        }
        return CompletableFuture.completedFuture(new int[]{0, Position.MAX_SEGMENT});
    }

    public CompletableFuture<MessageBatch> read(TrackerRead trackerRead) {
        if (trackerRead.getMessageType() != MessageType.RESULT && !Objects.equals(
                trackerRead.getTrackerId(), trackers.computeIfAbsent(
                        trackerRead.getConsumer(), c -> trackerRead).getTrackerId())) {
            log.debug("Delaying read by secondary tracker {} (message type {})", trackerRead.getConsumer(), messageType);
            return CompletableFuture.supplyAsync(
                    () -> new MessageBatch(new int[]{0, 0}, Collections.emptyList(), null),
                    CompletableFuture.delayedExecutor(trackerRead.getDeadline() - currentTimeMillis(), MILLISECONDS));
        }
        CompletableFuture<MessageBatch> result = new CompletableFuture<>();
        executor.execute(() -> {
            synchronized (InMemoryMessageStore.this) {
                Map<Long, SerializedMessage> tailMap = Collections.emptyMap();
                while (currentTimeMillis() < trackerRead.getDeadline()
                        && shouldWait(tailMap = messageLog
                        .tailMap(Optional.ofNullable(trackerRead.getLastIndex()).orElseGet(
                                () -> getLastIndex(trackerRead.getConsumer())), false))) {
                    long duration = trackerRead.getDeadline() - currentTimeMillis();
                    if (duration > 0) {
                        try {
                            this.wait(duration);
                        } catch (InterruptedException e) {
                            log.warn("Interrupted read of primary tracker {} (message type {})",
                                     trackerRead.getConsumer(), messageType);
                            currentThread().interrupt();
                        }
                    }
                }
                List<SerializedMessage> messages = new ArrayList<>(filterMessages(tailMap.values()));
                messages = messages.subList(0, Math.min(messages.size(), trackerRead.getMaxSize()));
                Long lastIndex = messages.isEmpty() ? null : messages.get(messages.size() - 1).getIndex();
                messages = messages.stream().filter(trackerRead::canHandle).collect(toList());
                result.complete(new MessageBatch(new int[]{0, 128}, messages, lastIndex));
            }
        });
        return result;
    }

    protected boolean shouldWait(Map<Long, SerializedMessage> tailMap) {
        return filterMessages(tailMap.values()).isEmpty();
    }

    protected Collection<SerializedMessage> filterMessages(Collection<SerializedMessage> messages) {
        return messages;
    }

    @Override
    public List<SerializedMessage> readFromIndex(long minIndex, int maxSize) {
        ArrayList<SerializedMessage> list = new ArrayList<>(messageLog.tailMap(minIndex).values());
        return list.subList(0, Math.min(maxSize, list.size()));
    }

    private long getLastIndex(String consumer) {
        return consumerTokens.computeIfAbsent(consumer, k -> -1L);
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex, Guarantee guarantee) {
        return resetPosition(consumer, lastIndex, guarantee);
    }

    @Override
    public Awaitable resetPosition(String consumer, long lastIndex, Guarantee guarantee) {
        consumerTokens.put(consumer, lastIndex);
        return Awaitable.ready();
    }

    @Override
    public Position getPosition(String consumer) {
        return Optional.ofNullable(consumerTokens.get(consumer)).map(Position::new).orElse(null);
    }

    @Override
    public Awaitable disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch, Guarantee guarantee) {
        disconnectTrackersMatching(t -> Objects.equals(trackerId, t.getTrackerId()));
        return Awaitable.ready();
    }

    @SuppressWarnings("unchecked")
    public <T extends TrackerRead> void disconnectTrackersMatching(Predicate<T> predicate) {
        trackers.values().removeIf(t -> predicate.test((T) t));
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    protected SerializedMessage getMessage(long index) {
        return messageLog.get(index);
    }
}
