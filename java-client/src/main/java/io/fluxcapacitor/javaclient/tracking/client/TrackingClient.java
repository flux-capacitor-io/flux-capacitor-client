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
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.SneakyThrows;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Low-level client interface for tracking and consuming messages from a message log.
 * <p>
 * A {@code TrackingClient} is responsible for retrieving message batches, claiming processing segments, and managing
 * tracking positions for a named consumer. Implementations typically include:
 * <ul>
 *   <li>An in-memory version for testing scenarios</li>
 *   <li>A WebSocket-backed implementation for connecting to the Flux Capacitor platform</li>
 * </ul>
 *
 * <p>Each message type (e.g., command, event, query) is backed by its own {@code TrackingClient} instance,
 * allowing independent consumption streams and tracking state.
 *
 * @see io.fluxcapacitor.javaclient.configuration.client.Client
 * @see io.fluxcapacitor.javaclient.tracking.Tracker
 */
public interface TrackingClient extends AutoCloseable {

    /**
     * Reads the next available {@link MessageBatch} for the given tracker and blocks until messages are available.
     * <p>
     * This is a convenience method that synchronously waits for {@link #read} to complete.
     *
     * @param trackerId     the unique identifier of the tracker requesting messages
     * @param lastIndex     the last successfully processed index
     * @param configuration the consumer configuration that determines segment, filters, and batch settings
     * @return the next available message batch (may be empty if disconnected)
     */
    @SneakyThrows
    default MessageBatch readAndWait(String trackerId, Long lastIndex,
                                     ConsumerConfiguration configuration) {
        return read(trackerId, lastIndex, configuration).get();
    }

    /**
     * Asynchronously reads the next available {@link MessageBatch} for a given tracker.
     *
     * @param trackerId             the unique ID for the tracker thread requesting messages
     * @param lastIndex             the last index successfully handled by this tracker
     * @param trackingConfiguration the full configuration for the consumer
     * @return a {@link CompletableFuture} that completes with the next batch of messages
     */
    CompletableFuture<MessageBatch> read(String trackerId, Long lastIndex,
                                         ConsumerConfiguration trackingConfiguration);

    /**
     * Fetches messages starting from the given index up to the provided max size.
     * <p>
     * This method bypasses consumer configurations and is primarily used for diagnostics or reprocessing.
     *
     * @param minIndex the starting index (inclusive)
     * @param maxSize  the maximum number of messages to retrieve
     * @return a list of serialized messages starting at the given index
     */
    List<SerializedMessage> readFromIndex(long minIndex, int maxSize);

    /**
     * Claims a processing segment for the given tracker.
     * <p>
     * Segments are used to partition the message log among multiple tracker threads for parallel processing.
     *
     * @param trackerId the unique identifier of the tracker attempting to claim a segment
     * @param lastIndex the tracker's last successfully processed index
     * @param config    the full consumer configuration
     * @return a {@link CompletableFuture} resolving to the result of the claim
     */
    CompletableFuture<ClaimSegmentResult> claimSegment(String trackerId, Long lastIndex,
                                                       ConsumerConfiguration config);

    /**
     * Stores the last successfully processed position for a consumer.
     * <p>
     * Default implementation uses {@link Guarantee#STORED} as the delivery guarantee.
     *
     * @param consumer  the name of the consumer
     * @param segment   the segment the tracker is processing
     * @param lastIndex the index up to which messages have been handled
     * @return a future indicating completion
     */
    default CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex) {
        return storePosition(consumer, segment, lastIndex, Guarantee.STORED);
    }

    /**
     * Stores the last successfully processed position for a consumer with a specific delivery guarantee.
     *
     * @param consumer  the name of the consumer
     * @param segment   the segment the tracker is processing
     * @param lastIndex the last message index processed
     * @param guarantee delivery guarantee (e.g., STORED, SENT)
     * @return a future indicating completion
     */
    CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex, Guarantee guarantee);

    /**
     * Resets the consumer's tracking position to a given index.
     * <p>
     * This is often used for replay, diagnostics, or recovery scenarios. Uses {@link Guarantee#STORED} by default.
     *
     * @param consumer  the name of the consumer
     * @param lastIndex the new index to start from
     * @return a future indicating completion
     */
    default CompletableFuture<Void> resetPosition(String consumer, long lastIndex) {
        return resetPosition(consumer, lastIndex, Guarantee.STORED);
    }

    /**
     * Resets the consumer's tracking position to a given index with a specific delivery guarantee.
     *
     * @param consumer  the name of the consumer
     * @param lastIndex the new index to start from
     * @param guarantee the delivery guarantee
     * @return a future indicating completion
     */
    CompletableFuture<Void> resetPosition(String consumer, long lastIndex, Guarantee guarantee);

    /**
     * Returns the current committed tracking position for the given consumer.
     *
     * @param consumer the name of the consumer
     * @return the last known committed position
     */
    Position getPosition(String consumer);

    /**
     * Disconnects the specified tracker from its segment and optionally sends an empty final batch.
     * <p>
     * Default implementation uses {@link Guarantee#SENT}.
     *
     * @param consumer            the name of the consumer group
     * @param trackerId           the ID of the tracker thread being disconnected
     * @param sendFinalEmptyBatch whether to send an empty batch to flush tracking state
     * @return a future indicating disconnection
     */
    default CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch) {
        return disconnectTracker(consumer, trackerId, sendFinalEmptyBatch, Guarantee.SENT);
    }

    /**
     * Disconnects the specified tracker from its segment with the specified delivery guarantee.
     *
     * @param consumer            the name of the consumer group
     * @param trackerId           the ID of the tracker thread being disconnected
     * @param sendFinalEmptyBatch whether to send a final empty batch to commit state
     * @param guarantee           the delivery guarantee to use
     * @return a future indicating disconnection
     */
    CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch, Guarantee guarantee);

    /**
     * Returns the {@link MessageType} (e.g., COMMAND, EVENT, QUERY) associated with this tracking client.
     *
     * @return the message type
     */
    MessageType getMessageType();

    /**
     * Returns the topic associated with this tracking client.
     * <p>
     * This is applicable only when {@link #getMessageType()} is {@link MessageType#DOCUMENT} or
     * {@link MessageType#CUSTOM}, where messages are organized into named topics beyond the standard type-based
     * categorization.
     *
     * <p>For other {@link MessageType}s (e.g., {@code COMMAND}, {@code EVENT}), the concept of a topic is implicit
     * and not required for tracking.
     *
     * @return the topic name, or {@code null} if not applicable for the message type
     */
    String getTopic();

    /**
     * Closes any open resources associated with this client.
     * <p>
     * Once closed, the client should no longer be used to fetch or commit tracking state.
     */
    @Override
    void close();
}
