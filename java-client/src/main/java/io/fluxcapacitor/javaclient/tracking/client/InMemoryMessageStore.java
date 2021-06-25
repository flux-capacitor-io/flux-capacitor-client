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
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.IndexUtils.indexFromMillis;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentClock;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

@Slf4j
@RequiredArgsConstructor
public class InMemoryMessageStore implements GatewayClient, TrackingClient {

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private final AtomicLong nextIndex = new AtomicLong();
    private final Map<String, TrackerRead> trackers = new ConcurrentHashMap<>();
    private final List<Consumer<SerializedMessage>> monitors = new CopyOnWriteArrayList<>();

    private final ConcurrentSkipListMap<Long, SerializedMessage> messageLog = new ConcurrentSkipListMap<>();
    private final Map<String, Long> consumerTokens = new ConcurrentHashMap<>();

    @Override
    public Awaitable send(SerializedMessage... messages) {
        Arrays.stream(messages).forEach(m -> {
            if (m.getIndex() == null) {
                m.setIndex(nextIndex.updateAndGet(i -> i <= 0 ? indexFromMillis(currentClock().millis()) : i + 1));
            }
            messageLog.put(m.getIndex(), m);
            monitors.forEach(monitor -> monitor.accept(m));
        });
        synchronized (this) {
            this.notifyAll();
        }
        return Awaitable.ready();
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId,
                                                Long previousLastIndex, ConsumerConfiguration configuration) {
        return read(new SimpleTrackerRead(consumer, trackerId, previousLastIndex, configuration));
    }

    public CompletableFuture<MessageBatch> read(TrackerRead trackerRead) {
        if (trackerRead.getMessageType() != MessageType.RESULT && !Objects.equals(
                trackerRead.getTrackerId(), trackers.computeIfAbsent(
                        trackerRead.getConsumerName(), c -> trackerRead).getTrackerId())) {
            return CompletableFuture.supplyAsync(
                    () -> new MessageBatch(new int[]{0, 0}, Collections.emptyList(), null),
                    CompletableFuture.delayedExecutor(trackerRead.getDeadline() - currentTimeMillis(), MILLISECONDS));
        }
        CompletableFuture<MessageBatch> result = new CompletableFuture<>();
        executorService.submit(() -> {
            synchronized (this) {
                Map<Long, SerializedMessage> tailMap = Collections.emptyMap();
                while (currentTimeMillis() < trackerRead.getDeadline()
                        && shouldWait(tailMap = messageLog
                        .tailMap(Optional.ofNullable(trackerRead.getLastTrackerIndex()).orElseGet(
                                () -> getLastIndex(trackerRead.getConsumerName())), false))) {
                    try {
                        this.wait(trackerRead.getDeadline() - currentTimeMillis());
                    } catch (InterruptedException e) {
                        currentThread().interrupt();
                    }
                }
                List<SerializedMessage> messages = filterMessages(new ArrayList<>(tailMap.values()));
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

    protected List<SerializedMessage> filterMessages(Collection<SerializedMessage> messages) {
        if (messages.isEmpty()) {
            return Collections.emptyList();
        }
        return messages instanceof List<?> ? (List<SerializedMessage>) messages : new ArrayList<>(messages);
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
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        return resetPosition(consumer, lastIndex);
    }

    @Override
    public Awaitable resetPosition(String consumer, long lastIndex) {
        consumerTokens.put(consumer, lastIndex);
        return Awaitable.ready();
    }

    @Override
    public Awaitable disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch) {
        disconnectTrackersMatching(t -> Objects.equals(trackerId, t.getTrackerId()));
        return Awaitable.ready();
    }

    @SuppressWarnings("unchecked")
    public <T extends TrackerRead> void disconnectTrackersMatching(Predicate<T> predicate) {
        trackers.values().removeIf(t -> predicate.test((T) t));
    }

    @Override
    public Registration registerMonitor(Consumer<SerializedMessage> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }

    @Override
    public void close() {
        executorService.shutdown();
    }

    protected SerializedMessage getMessage(long index) {
        return messageLog.get(index);
    }
}
