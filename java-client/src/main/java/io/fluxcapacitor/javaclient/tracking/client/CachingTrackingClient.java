/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
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

/**
 * A {@link TrackingClient} implementation that wraps another client (typically a {@link WebsocketTrackingClient})
 * and caches recent messages in memory to reduce redundant round trips to the Flux platform.
 * <p>
 * This client is particularly useful in environments where multiple consumers or trackers are processing the
 * same stream of messages. Rather than each tracker reading from the backend individually, a shared in-memory cache
 * serves recent messages directly when possible.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Internally starts a special tracker that continuously appends new messages to a bounded in-memory cache.</li>
 *   <li>Trackers that read from this client are first served from the local cache when possible.</li>
 *   <li>Falls back to the delegate {@link TrackingClient} for uncached or missed messages.</li>
 *   <li>Trackers waiting for new messages are notified via scheduled polling or real-time cache updates.</li>
 *   <li>Cache size is limited via {@code maxCacheSize}; old messages are evicted in insertion order.</li>
 * </ul>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Optimizing performance when many trackers are polling the same stream concurrently</li>
 *   <li>Reducing network latency and load on the Flux platform for high-volume message types</li>
 *   <li>Minimizing end-to-end processing delay in horizontally scaled applications</li>
 * </ul>
 *
 * <h2>Tracking Mechanics</h2>
 * <ul>
 *   <li>Uses a background tracker configured with {@code ignoreSegment = true} and {@code clientControlledIndex = true}
 *       to stream all new messages into the cache.</li>
 *   <li>Trackers calling {@link #read} are served from the cache if their {@code lastIndex} is already present.</li>
 *   <li>If not, the delegate is queried directly to maintain completeness.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>The cache is backed by a {@link ConcurrentSkipListMap} for safe concurrent access.</li>
 *   <li>Eviction and tracker notifications are synchronized to prevent race conditions.</li>
 * </ul>
 *
 * @see TrackingClient
 * @see WebsocketTrackingClient
 * @see FluxCapacitor
 */
@RequiredArgsConstructor
@Slf4j
public class CachingTrackingClient implements TrackingClient {
    @Getter
    private final TrackingClient delegate;
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
    public CompletableFuture<MessageBatch> read(String trackerId, Long lastIndex, ConsumerConfiguration config) {
        if (started.compareAndSet(false, true)) {
            ConsumerConfiguration cacheFillerConfig = ConsumerConfiguration.builder()
                    .ignoreSegment(true)
                    .clientControlledIndex(true)
                    .minIndex(IndexUtils.indexForCurrentTime())
                    .name(CachingTrackingClient.class.getSimpleName()).build();
            registration = FluxCapacitor.getOptionally()
                    .map(fc -> DefaultTracker.start(this::cacheNewMessages, delegate.getMessageType(),
                                                    delegate.getTopic(), cacheFillerConfig, fc))
                    .orElseGet(() -> DefaultTracker.start(this::cacheNewMessages, cacheFillerConfig, delegate));
        }
        if (lastIndex != null && cache.containsKey(lastIndex)) {
            Instant deadline = now().plus(config.getMaxWaitDuration());
            return delegate.claimSegment(trackerId, lastIndex, config).thenCompose(r -> {
                Long minIndex = r.getPosition().lowestIndexForSegment(r.getSegment()).orElse(null);
                if (minIndex != null) {
                    CompletableFuture<MessageBatch> result = new CompletableFuture<>();
                    MessageBatch messageBatch = getMessageBatch(config, minIndex, r);
                    if (!messageBatch.isEmpty()) {
                        result.complete(messageBatch);
                    } else {
                        waitForMessages(trackerId,
                                        ofNullable(messageBatch.getLastIndex()).orElse(minIndex),
                                        config, r, deadline, result);
                    }
                    return result;
                }
                return delegate.read(trackerId, lastIndex, config);
            });
        }
        return delegate.read(trackerId, lastIndex, config);
    }

    private void waitForMessages(String trackerId, long minIndex,
                                 ConsumerConfiguration config,
                                 ClaimSegmentResult claimResult, Instant deadline,
                                 CompletableFuture<MessageBatch> future) {
        AtomicLong atomicIndex = new AtomicLong(minIndex);
        long timeout = Duration.between(now(), deadline).toMillis();
        if (timeout <= 0) {
            future.complete(new MessageBatch(claimResult.getSegment(), List.of(), atomicIndex.get(), claimResult.getPosition(), true));
        } else {
            ScheduledFuture<?> timeoutSchedule = scheduler.schedule(() -> {
                try {
                    if (future.complete(
                            new MessageBatch(claimResult.getSegment(), List.of(), atomicIndex.get(), claimResult.getPosition(), true))) {
                        waitingTrackers.remove(trackerId);
                    }
                } finally {
                    if (atomicIndex.get() > minIndex) {
                        try {
                            storePosition(config.getName(), claimResult.getSegment(), atomicIndex.get()).get();
                        } catch (Exception e) {
                            log.error("Failed to update position of {}", config.getName(), e);
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
                config.getMaxFetchSize()).collect(toList());
        Long lastIndex = unfiltered.isEmpty() ? null : unfiltered.getLast().getIndex();
        return new MessageBatch(claim.getSegment(), filterMessages(
                unfiltered, claim.getSegment(), claim.getPosition(), config), lastIndex, claim.getPosition(),
                                unfiltered.size() < config.getMaxFetchSize());
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
    public List<SerializedMessage> readFromIndex(long minIndex, int maxSize) {
        return delegate.readFromIndex(minIndex, maxSize);
    }

    @Override
    public CompletableFuture<ClaimSegmentResult> claimSegment(String trackerId, Long lastIndex,
                                                              ConsumerConfiguration config) {
        return delegate.claimSegment(trackerId, lastIndex, config);
    }

    @Override
    public CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex, Guarantee guarantee) {
        return delegate.storePosition(consumer, segment, lastIndex, guarantee);
    }

    @Override
    public CompletableFuture<Void> resetPosition(String consumer, long lastIndex, Guarantee guarantee) {
        return delegate.resetPosition(consumer, lastIndex, guarantee);
    }

    @Override
    public Position getPosition(String consumer) {
        return delegate.getPosition(consumer);
    }

    @Override
    public CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch, Guarantee guarantee) {
        return delegate.disconnectTracker(consumer, trackerId, sendFinalEmptyBatch, guarantee);
    }

    @Override
    public MessageType getMessageType() {
        return delegate.getMessageType();
    }

    @Override
    public String getTopic() {
        return delegate.getTopic();
    }

    @Override
    public void close() {
        ofNullable(registration).ifPresent(Registration::cancel);
        scheduler.shutdown();
        delegate.close();
    }
}
