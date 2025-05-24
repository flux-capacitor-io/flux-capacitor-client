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

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;

import java.util.Comparator;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.TimingUtils.isMissedDeadline;

/**
 * A {@code Tracker} represents an active consumer of messages for a particular {@code ConsumerConfiguration}.
 * <p>
 * Trackers are responsible for handling a range of message segments and coordinating how and when messages are
 * delivered from the {@link MessageStore} or other sources. They are typically managed by a {@link TrackingStrategy}.
 *
 * <p>Each tracker identifies a specific consumer (via {@link #getConsumerName()}) and client instance (via
 * {@link #getClientId()}), and may support additional filtering by message type or target.
 *
 * <h2>Responsibilities</h2>
 * A {@code Tracker} is responsible for:
 * <ul>
 *     <li>Receiving batches of messages via {@link #send(MessageBatch)}</li>
 *     <li>Declaring the range of segments it is responsible for</li>
 *     <li>Filtering messages by type, target, and segment hash</li>
 *     <li>Tracking its last consumed index and activity deadline</li>
 * </ul>
 *
 * <p>Trackers may also participate in client-controlled index tracking, message purging, and deadline-based disconnection.
 */
public interface Tracker extends Comparable<Tracker> {

    /**
     * Default comparator based on consumer name and tracker ID.
     */
    Comparator<Tracker> comparator = Comparator.comparing(Tracker::getConsumerName)
            .thenComparing(Tracker::getTrackerId);

    /**
     * @return the logical name of the consumer this tracker belongs to.
     */
    String getConsumerName();

    /**
     * @return the unique ID of the client, typically one per client application instance.
     */
    String getClientId();

    /**
     * @return the unique ID of this tracker instance.
     */
    String getTrackerId();

    /**
     * @return the index of the last successfully consumed message, or {@code null} if uninitialized.
     */
    Long getLastTrackerIndex();

    /**
     * @return the maximum number of messages this tracker wants to consume in a single batch.
     */
    int getMaxSize();

    /**
     * @return the system deadline (epoch millis) by which this tracker expects a new batch. If this deadline is missed,
     * the tracker should be given an empty batch.
     */
    long getDeadline();

    /**
     * @return the maximum time window (in millis) after which a batch should be delivered even if no messages are
     * available.
     */
    long maxTimeout();

    /**
     * @return the optional duration (in milliseconds) after which this tracker may be purged if it is actively
     * processing messages (i.e., not idle or waiting).
     * <p>
     * This mechanism ensures that stale trackers are eventually cleaned up, particularly in scenarios where graceful
     * disconnection is not guaranteed. This is especially relevant for <i>external trackers</i> — such as non-Java
     * applications or clients not using Flux’s built-in client — which may not send an explicit disconnect.
     * <p>
     * In normal Flux client usage, purging should rarely occur, as disconnection is managed by the client.
     */
    Long getPurgeDelay();

    /**
     * @return {@code true} if the tracker should only receive messages that explicitly target its client ID.
     * <p>
     * When enabled, this filter ensures that only {@link SerializedMessage messages} with a {@code target} matching
     * {@link #getClientId()} are considered valid. This can be useful for isolating messages meant for a specific
     * client.
     * <p>
     * When disabled (the default), all messages in the segment range are considered, regardless of their target.
     */
    default boolean isFilterMessageTarget() {
        return false;
    }

    /**
     * @return {@code true} if this tracker ignores segment-based filtering (i.e. processes all segments).
     */
    boolean ignoreSegment();

    /**
     * @return {@code true} if this tracker uses client-controlled index tracking (instead of store-side indexing).
     */
    boolean clientControlledIndex();

    /**
     * @return {@code true} if this tracker is the only one allowed to handle messages for its consumer. This is useful
     * for single-tracker consumer configurations.
     */
    default boolean singleTracker() {
        return false;
    }

    /**
     * Returns a predicate for filtering messages based on their type (class name).
     *
     * @return a type filter predicate. By default, allows all types.
     */
    default Predicate<String> getTypeFilter() {
        return s -> true;
    }

    /**
     * Sends a batch of messages to this tracker.
     *
     * @param batch the batch to deliver
     */
    void send(MessageBatch batch);

    /**
     * Sends an empty batch (typically used to signal idle or shutdown). Default implementation forwards to
     * {@link #send(MessageBatch)}.
     *
     * @param batch an empty batch instance
     */
    default void sendEmptyBatch(MessageBatch batch) {
        send(batch);
    }

    /**
     * Returns a copy of this tracker with its last index updated.
     *
     * @param lastTrackerIndex the new index value
     * @return a modified tracker instance
     */
    Tracker withLastTrackerIndex(Long lastTrackerIndex);

    /**
     * Checks if the given message can be handled by this tracker based on segment range and type filtering.
     *
     * @param message      the message to check
     * @param segmentRange the range of segments this tracker is assigned to
     * @return {@code true} if the message is valid for this tracker
     */
    default boolean canHandle(SerializedMessage message, int[] segmentRange) {
        return isValidTarget(message, segmentRange) && isValidType(message);
    }

    /**
     * @return {@code true} if this tracker has missed its deadline.
     */
    default boolean hasMissedDeadline() {
        return isMissedDeadline(getDeadline());
    }

    /**
     * Internal helper to check if this tracker is a valid target for a given {@link SerializedMessage} given the
     * tracker's segment range.
     */
    private boolean isValidTarget(SerializedMessage message, int[] segmentRange) {
        String target = message.getTarget();
        if (isFilterMessageTarget() && target != null) {
            if (target.equals(getTrackerId())) {
                return true;
            }
            if (!target.equals(getClientId())) {
                return false;
            }
        }
        return contains(message, segmentRange);
    }

    /**
     * Internal helper to check segment inclusion.
     */
    private boolean contains(SerializedMessage message, int[] segmentRange) {
        if (singleTracker()) {
            return segmentRange[0] == 0 && segmentRange[0] != segmentRange[1];
        }
        if (ignoreSegment()) {
            return true;
        }
        return ConsistentHashing.fallsInRange(message.getSegment(), segmentRange);
    }

    /**
     * Internal helper to check type filter.
     */
    private boolean isValidType(SerializedMessage message) {
        return message.getData().getType() == null || getTypeFilter().test(message.getData().getType());
    }

    /**
     * Compares trackers based on consumer and tracker IDs for stable sorting.
     */
    @Override
    default int compareTo(Tracker o) {
        return comparator.compare(this, o);
    }
}
