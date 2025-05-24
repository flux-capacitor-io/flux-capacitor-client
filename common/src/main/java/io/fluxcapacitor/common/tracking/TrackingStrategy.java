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

import java.io.Closeable;
import java.util.function.Predicate;

/**
 * A {@code TrackingStrategy} defines how a {@link Tracker} consumes messages from a message log or distributed segment space.
 * <p>
 * This interface enables pluggable strategies for message consumption and parallelism. Depending on the configuration and
 * type of tracker, a strategy may either:
 * <ul>
 *     <li>Fetch and supply message batches directly from the message store (tailing a log), or</li>
 *     <li>Negotiate segment claims and delegate message retrieval to the tracker itself (client-side tracking).</li>
 * </ul>
 *
 * <p>Tracking strategies are a key part of Flux's support for distributed, fault-tolerant, and parallel message handling.
 *
 * <h2>Responsibilities</h2>
 * A {@code TrackingStrategy} is responsible for:
 * <ul>
 *     <li>Providing the next batch of messages for a {@link Tracker} (via {@link #getBatch})</li>
 *     <li>Assigning ownership of message segments (via {@link #claimSegment})</li>
 *     <li>Disconnecting active trackers when needed (via {@link #disconnectTrackers})</li>
 * </ul>
 *
 * <p>Implementations should be thread-safe and able to handle dynamic tracker registration and segment rebalancing.
 */
public interface TrackingStrategy extends Closeable {

    /**
     * Requests a new batch of messages for the given tracker.
     * <p>
     * This method is typically invoked by the {@link Tracker} when it is ready to handle more messages.
     * Depending on the strategy, this method may:
     * <ul>
     *     <li>Fetch messages directly from a {@link MessageStore} and deliver them to the tracker (e.g. for log tailing), or</li>
     *     <li>Suspend the tracker until messages become available</li>
     * </ul>
     *
     * @param tracker        the tracker requesting a batch
     * @param positionStore  to fetch or update tracking positions
     */
    void getBatch(Tracker tracker, PositionStore positionStore);

    /**
     * Claims one or more message segments for the given tracker.
     * <p>
     * This method is invoked when segment-based partitioning is enabled. It ensures that each segment is only claimed
     * by a single tracker at a time and may release conflicting claims if necessary.
     *
     * @param tracker        the tracker attempting to claim a segment
     * @param positionStore  to fetch tracking positions
     */
    void claimSegment(Tracker tracker, PositionStore positionStore);

    /**
     * Disconnects trackers that match the provided filter.
     * <p>
     * This is typically used during client shutdown, reconfiguration, or error handling to forcibly remove
     * trackers from the strategy's internal registry.
     *
     * @param predicate         filter for matching trackers to disconnect
     * @param sendFinalBatch    if {@code true}, a final empty batch should be sent to each disconnected tracker
     *                          to allow graceful termination
     */
    void disconnectTrackers(Predicate<Tracker> predicate, boolean sendFinalBatch);

    /**
     * Closes the tracking strategy and releases any underlying resources.
     */
    @Override
    void close();
}
