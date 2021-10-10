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

import io.fluxcapacitor.common.IndexUtils;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.ConsistentHashing.computeSegment;
import static java.time.Instant.now;
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@RequiredArgsConstructor
@Slf4j
public class CachingTrackingClient implements TrackingClient {
    @Delegate(excludes = Overrides.class)
    private final WebsocketTrackingClient delegate;
    private final int maxCacheSize;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Registration registration;

    private final ConcurrentSkipListMap<Long, SerializedMessage> cache = new ConcurrentSkipListMap<>();
    private final Map<String, Runnable> waitingTrackers = new ConcurrentHashMap<>();

    public CachingTrackingClient(WebsocketTrackingClient delegate) {
        this(delegate, 1024);
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId, Long lastIndex,
                                                ConsumerConfiguration config) {
        if (started.compareAndSet(false, true)) {
            ConsumerConfiguration cacheFillerConfig = ConsumerConfiguration.builder()
                    .messageType(config.getMessageType()).ignoreSegment(true)
                    .lastIndex(IndexUtils.indexForCurrentTime())
                    .name(CachingTrackingClient.class.getSimpleName()).build();
            registration = FluxCapacitor.getOptionally()
                    .map(fc -> DefaultTracker.start(this::cacheNewMessages, cacheFillerConfig, fc.client()))
                    .orElseGet(() -> DefaultTracker.start(this::cacheNewMessages, cacheFillerConfig, delegate));
        }
        if (lastIndex != null && cache.containsKey(lastIndex)) {
            Instant deadline = now().plus(config.getMaxWaitDuration());
            return delegate.claimSegment(consumer, trackerId, lastIndex, config).thenCompose(r -> {
                Long minIndex = r.getPosition().lowestIndexForSegment(r.getSegment()).orElse(null);
                if (minIndex != null) {
                    CompletableFuture<MessageBatch> result = new CompletableFuture<>();
                    MessageBatch messageBatch = getMessageBatch(config, minIndex, r);
                    if (!messageBatch.isEmpty()) {
                        result.complete(messageBatch);
                    } else {
                        waitForMessages(consumer, trackerId,
                                        ofNullable(messageBatch.getLastIndex()).orElse(minIndex),
                                        config, r, deadline, result);
                    }
                    return result;
                }
                return delegate.read(consumer, trackerId, lastIndex, config);
            });
        }
        return delegate.read(consumer, trackerId, lastIndex, config);
    }

    private void waitForMessages(String consumer, String trackerId, long minIndex,
                                 ConsumerConfiguration config,
                                 ClaimSegmentResult claimResult, Instant deadline,
                                 CompletableFuture<MessageBatch> future) {
        AtomicLong atomicIndex = new AtomicLong(minIndex);
        long timeout = Duration.between(now(), deadline).toMillis();
        if (timeout <= 0) {
            future.complete(new MessageBatch(claimResult.getSegment(), List.of(), atomicIndex.get()));
        } else {
            ScheduledFuture<?> timeoutSchedule = scheduler.schedule(() -> {
                try {
                    if (future.complete(
                            new MessageBatch(claimResult.getSegment(), List.of(), atomicIndex.get()))) {
                        waitingTrackers.remove(trackerId);
                    }
                } finally {
                    if (atomicIndex.get() > minIndex) {
                        try {
                            storePosition(consumer, claimResult.getSegment(), atomicIndex.get()).await();
                        } catch (Exception e) {
                            log.error("Failed to update position of {}", consumer, e);
                        }
                    }
                }
            }, timeout, MILLISECONDS);
            Runnable fetchTask = new Runnable() {
                @Override
                public void run() {
                    MessageBatch batch = getMessageBatch(config, atomicIndex.get(), claimResult);
                    if (!batch.isEmpty() && future.complete(batch) && waitingTrackers.remove(trackerId, this)) {
                        timeoutSchedule.cancel(false);
                    } else {
                        atomicIndex.updateAndGet(c -> Optional.ofNullable(batch.getLastIndex()).orElse(c));
                    }
                }
            };
            waitingTrackers.put(trackerId, fetchTask);
        }
    }

    protected MessageBatch getMessageBatch(ConsumerConfiguration config, long minIndex, ClaimSegmentResult claim) {
        List<SerializedMessage> unfiltered = cache.tailMap(minIndex, false).values().stream().limit(
                config.getMaxFetchBatchSize()).collect(toList());
        Long lastIndex = unfiltered.isEmpty() ? null : unfiltered.get(unfiltered.size() - 1).getIndex();
        return new MessageBatch(claim.getSegment(), filterMessages(
                unfiltered, claim.getSegment(), claim.getPosition(), config), lastIndex);
    }


    protected List<SerializedMessage> filterMessages(List<SerializedMessage> messages, int[] segmentRange,
                                                     Position position, ConsumerConfiguration config) {
        if (messages.isEmpty()) {
            return messages;
        }
        Predicate<SerializedMessage> predicate
                = m -> (config.getTypeFilter() == null || m.getData().getType() == null
                || config.getTypeFilter().matches(m.getData().getType())) && position.isNewMessage(m);
        if (!config.ignoreSegment()) {
            predicate = predicate.and(m -> segmentRange[1] != 0 && m.getSegment() >= segmentRange[0]
                    && m.getSegment() < segmentRange[1]);
        }
        return messages.stream().filter(predicate).collect(toList());
    }

    protected void cacheNewMessages(List<SerializedMessage> messages) {
        if (!messages.isEmpty()) {
            Map<Long, SerializedMessage> messageMap = messages.stream().peek(m -> m.setSegment(
                            m.getSegment() == null ? computeSegment(m.getMessageId(), Position.MAX_SEGMENT) :
                                    m.getSegment() % Position.MAX_SEGMENT))
                    .collect(toMap(SerializedMessage::getIndex, Function.identity()));
            cache.putAll(messageMap);
            waitingTrackers.values().forEach(Runnable::run);
            removeOldMessages();
        }
    }

    protected synchronized void removeOldMessages() {
        int removeCount = cache.size() - maxCacheSize;
        for (int i = 0; i < removeCount; i++) {
            cache.pollFirstEntry();
        }
    }

    @Override
    public void close() {
        ofNullable(registration).ifPresent(Registration::cancel);
        scheduler.shutdown();
        delegate.close();
    }

    protected interface Overrides {
        CompletableFuture<MessageBatch> read(String consumer, String trackerId, Long lastIndex,
                                             ConsumerConfiguration trackingConfiguration);

        MessageBatch readAndWait(String consumer, String trackerId, Long lastIndex,
                                 ConsumerConfiguration configuration);

        void close();
    }
}
