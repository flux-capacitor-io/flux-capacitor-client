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

package io.fluxcapacitor.common.tracking;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.common.application.AutoClosing;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.ConsistentHashing.computeSegment;
import static io.fluxcapacitor.common.api.tracking.Position.MAX_SEGMENT;
import static io.fluxcapacitor.common.api.tracking.Position.newPosition;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.Collections.synchronizedMap;
import static java.util.Optional.ofNullable;

/**
 * Streaming strategy that allows multiple clients to concurrently consume a message stream. Messages are routed to
 * clients based on the value of their segment. Each connected client handles a distinct range of segments.
 * <p>
 * Message segments are determined by the clients that publish the messages (usually based on the consistent hash of
 * some routing key, like the value of a user id).
 * <p>
 * If a client joins or leaves the cluster the segment range mapped to each client is recalculated so messages may get
 * routed differently than before.
 * <p>
 * Clients can safely join or leave the cluster at any time. The strategy guarantees that a message is not consumed by
 * more than one client.
 */
@Slf4j
public class DefaultTrackingStrategy extends AutoClosing implements TrackingStrategy {

    private final MessageStore source;
    private final TaskScheduler scheduler;
    private final int segments;
    private final Map<Tracker, WaitingTracker> waitingTrackers = synchronizedMap(new HashMap<>());
    private final ConcurrentHashMap<String, TrackerCluster> clusters = new ConcurrentHashMap<>();
    private volatile long lastSeenIndex = -1L;

    public DefaultTrackingStrategy(MessageStore source) {
        this(source, new InMemoryTaskScheduler());
    }

    public DefaultTrackingStrategy(MessageStore source, TaskScheduler scheduler) {
        this(source, scheduler, MAX_SEGMENT);
    }

    protected DefaultTrackingStrategy(MessageStore source, TaskScheduler scheduler, int segments) {
        this.source = source;
        this.scheduler = scheduler;
        this.segments = segments;
        source.registerMonitor(this::onUpdate);
        purgeCeasedTrackers(Duration.ofSeconds(2));
    }

    @Override
    public void getBatch(Tracker tracker, PositionStore positionStore) {
        TrackerCluster oldCluster = clusters.get(tracker.getConsumerName());
        int[] newSegment = claimSegment(tracker);
        try {
            if (newSegment[0] == newSegment[1]) {
                waitForMessages(tracker, new MessageBatch(newSegment, emptyList(), null, newPosition()), positionStore);
                return;
            }
            int batchSize = adjustMaxSize(tracker, tracker.getMaxSize());

            long lastIndex = lastSeenIndex;
            List<SerializedMessage> unfiltered, filtered;
            Position position;
            do {
                position = position(tracker, positionStore, newSegment);
                unfiltered = getBatch(newSegment, position, batchSize);
                filtered = filter(unfiltered, newSegment, position, tracker);

                if (!unfiltered.isEmpty() && filtered.isEmpty()) {
                    long batchIndex = unfiltered.get(unfiltered.size() - 1).getIndex();

                    if (batchIndex < indexFromMillis(System.currentTimeMillis() - tracker.maxTimeout())) {
                        //if the index is old, send back an empty batch.
                        // Prevents rushing through potentially billions of messages
                        MessageBatch emptyBatch = new MessageBatch(newSegment, filtered, batchIndex, position);
                        tracker.send(emptyBatch);
                        return;
                    } else {
                        //update stored position and tracker, otherwise client may stay endlessly waiting
                        positionStore.storePosition(tracker.getConsumerName(), newSegment, batchIndex);
                        tracker = tracker.withLastTrackerIndex(batchIndex);
                    }
                }
            } while (!unfiltered.isEmpty() && filtered.isEmpty() && !tracker.hasMissedDeadline());

            MessageBatch messageBatch = new MessageBatch(newSegment, filtered, getLastIndex(unfiltered), position);
            if (messageBatch.isEmpty()) {
                waitForMessages(tracker, messageBatch, positionStore);
                if (lastIndex < lastSeenIndex
                    && (messageBatch.getLastIndex() == null || messageBatch.getLastIndex() < lastSeenIndex)) {
                    var task = waitingTrackers.get(tracker);
                    if (task != null && task.tracker == tracker) {
                        task.run();
                    }
                }
            } else {
                tracker.send(messageBatch);
            }
        } catch (Throwable e) {
            log.error("Failed to get a batch for tracker {}", tracker, e);
            waitForMessages(tracker, new MessageBatch(newSegment, emptyList(), null, newPosition()), positionStore);
        } finally {
            if (oldCluster != null && !Objects.deepEquals(oldCluster.getSegment(tracker), newSegment)) {
                onClusterUpdate(oldCluster);
            }
        }
    }

    @Override
    public void claimSegment(Tracker tracker, PositionStore positionStore) {
        int[] newSegment = claimSegment(tracker);
        if (newSegment[0] == newSegment[1]) {
            waitForUpdate(tracker, new MessageBatch(newSegment, emptyList(), null, newPosition()),
                          () -> claimSegment(tracker, positionStore));
        } else {
            tracker.send(new MessageBatch(newSegment, emptyList(), null,
                                          position(tracker, positionStore, newSegment)));
        }
    }

    protected List<SerializedMessage> getBatch(int[] segment, Position position, int batchSize) {
        return source.getBatch(position.lowestIndexForSegment(segment).orElse(null), batchSize);
    }

    protected void waitForMessages(Tracker tracker, MessageBatch emptyBatch, PositionStore positionStore) {
        waitForUpdate(tracker, emptyBatch, () -> getBatch(tracker, positionStore));
    }

    protected void waitForUpdate(Tracker tracker, MessageBatch emptyBatch, Runnable followUp) {
        if (tracker.hasMissedDeadline()) {
            tracker.send(emptyBatch);
            return;
        }
        clusters.compute(tracker.getConsumerName(), (p, c) -> ofNullable(c)
                .orElseGet(() -> new TrackerCluster(segments)).withWaitingTracker(tracker));
        Registration scheduleToken = scheduler.schedule(tracker.getDeadline(), () -> {
            if (waitingTrackers.keySet().removeIf(t -> t == tracker)) {
                clusters.compute(tracker.getConsumerName(), (p, cluster) -> cluster != null && cluster.contains(tracker)
                        ? cluster.withActiveTracker(tracker) : cluster);
                tracker.send(emptyBatch);
            }
        });
        WaitingTracker existing = waitingTrackers.remove(tracker);
        waitingTrackers.put(tracker, new WaitingTracker(tracker, scheduleToken, followUp));
        if (existing != null) {
            log.warn("Tracker replaced another waiting tracker. This should normally not happen. New tracker: {}",
                     tracker);
            existing.tracker.send(emptyBatch);
        }
    }

    protected Position position(Tracker tracker, PositionStore positionStore, int[] segment) {
        if (tracker.clientControlledIndex()) {
            Optional<Position> position = ofNullable(tracker.getLastTrackerIndex()).map(i -> new Position(segment, i));
            if (position.isPresent()) {
                return position.get();
            }
        }
        Position position = positionStore.position(tracker.getConsumerName());
        if (position.isNew(segment)) {
            if (tracker.getLastTrackerIndex() != null) {
                return new Position(segment, tracker.getLastTrackerIndex());
            }
            return new Position(segment, indexFromMillis(System.currentTimeMillis() - 1000L));
        }
        return position;
    }

    protected List<SerializedMessage> filter(List<SerializedMessage> messages, int[] segmentRange,
                                             Position position, Tracker tracker) {
        return messages.stream().filter(
                m -> tracker.canHandle(ensureMessageSegment(m), segmentRange)
                     && (tracker.ignoreSegment() || position.isNewMessage(m))).toList();
    }

    protected SerializedMessage ensureMessageSegment(SerializedMessage message) {
        message.setSegment(message.getSegment() == null ? computeSegment(
                message.getMessageId(), segments) : message.getSegment() % segments);
        return message;
    }

    protected int adjustMaxSize(Tracker tracker, int maxSize) {
        return ofNullable(clusters.get(tracker.getConsumerName()))
                .map(cluster -> cluster.getTrackers().size() * maxSize).orElse(maxSize);
    }

    protected int[] claimSegment(Tracker tracker) {
        TrackerCluster cluster = clusters.compute(tracker.getConsumerName(), (p, c) -> ofNullable(c)
                .orElseGet(() -> new TrackerCluster(segments)).withActiveTracker(tracker));
        return cluster.getSegment(tracker);
    }

    protected void onUpdate(List<SerializedMessage> messages) {
        if (!isStopped()) {
            synchronized (waitingTrackers) {
                lastSeenIndex = ofNullable(getLastIndex(messages)).orElse(lastSeenIndex);
                new ArrayList<>(waitingTrackers.values()).forEach(WaitingTracker::run);
            }
        }
    }

    protected void onClusterUpdate(TrackerCluster cluster) {
        if (!isStopped()) {
            synchronized (waitingTrackers) {
                waitingTrackers.entrySet().stream().filter(e -> cluster.contains(e.getKey())).map(
                        Map.Entry::getValue).toList().forEach(WaitingTracker::run);
            }
        }
    }

    @Override
    public void disconnectTrackers(Predicate<Tracker> predicate, boolean sendFinalEmptyBatch) {
        Set<Tracker> removedAndWaiting = new HashSet<>();
        Set<TrackerCluster> updatedClusters = new HashSet<>();
        try {
            synchronized (waitingTrackers) {
                waitingTrackers.keySet().removeIf(tracker -> {
                    boolean match = predicate.test(tracker);
                    if (match) {
                        removedAndWaiting.add(tracker);
                    }
                    return match;
                });
                clusters.replaceAll((key, cluster) -> {
                    var updatedCluster = cluster.purgeTrackers(predicate);
                    if (!Objects.equals(updatedCluster, cluster) && !updatedCluster.isEmpty()) {
                        updatedClusters.add(updatedCluster);
                    }
                    return updatedCluster;
                });
                clusters.values().removeIf(TrackerCluster::isEmpty);
            }
            updatedClusters.forEach(this::onClusterUpdate);
        } finally {
            if (sendFinalEmptyBatch) {
                removedAndWaiting.forEach(tracker -> {
                    try {
                        tracker.send(new MessageBatch(new int[]{0, 0}, emptyList(), null, newPosition()));
                    } catch (Exception e) {
                        log.error("Failed to send final empty batch to disconnecting tracker: {}", predicate, e);
                    }
                });
            }
        }
    }

    protected void purgeCeasedTrackers(Duration delay) {
        scheduler.schedule(currentTimeMillis() + delay.toMillis(), () -> {
            clusters.replaceAll((key, cluster) -> {
                TrackerCluster after = cluster.purgeTrackers(
                        t -> t.getPurgeDelay() != null && cluster.getProcessingDuration(t)
                                .filter(d -> d.toMillis() > t.getPurgeDelay()).isPresent());
                if (after != cluster) {
                    Set<Tracker> removed = new HashSet<>(cluster.getTrackers());
                    removed.removeAll(after.getTrackers());
                    if (!removed.isEmpty()) {
                        log.warn("Purged trackers from consumer {} because they have ceased processing: {}", key,
                                 removed);
                        return after;
                    }
                }
                return cluster;
            });
            purgeCeasedTrackers(delay);
        });
    }

    private Long getLastIndex(List<SerializedMessage> messages) {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1).getIndex();
    }

    private static long indexFromMillis(long millisSinceEpoch) {
        return millisSinceEpoch << 16;
    }

    @Override
    protected void onShutdown() {
        scheduler.shutdown();
    }

    @AllArgsConstructor
    protected class WaitingTracker implements Runnable {
        private final Tracker tracker;
        private final Registration scheduleToken;
        private final Runnable followUp;

        @Override
        public void run() {
            try {
                scheduleToken.cancel();
                if (waitingTrackers.remove(tracker, this)) {
                    followUp.run();
                }
            } catch (Throwable e) {
                log.error("Failed to execute tracker fetch / follow up", e);
            }
        }
    }
}
