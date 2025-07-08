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

package io.fluxcapacitor.common.api.eventsourcing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Represents a batch of serialized events for a specific aggregate.
 * <p>
 * An {@code EventBatch} groups one or more {@link SerializedMessage} instances that belong to the same aggregate.
 * Batches are typically appended atomically to the event store and processed in order.
 * <p>
 * This class is used within {@link AppendEvents} to persist updates to aggregates in event-sourced systems.
 *
 * <h2>Usage</h2>
 * - All events in a batch must refer to the same aggregate ID. - A batch may be marked as {@code storeOnly} to indicate
 * that the events should not be published to event handlers.
 *
 * @see SerializedMessage
 * @see AppendEvents
 */
@Value
public class EventBatch {

    /**
     * The identifier of the aggregate to which these events belong.
     */
    String aggregateId;

    /**
     * The list of serialized event messages to be appended.
     */
    List<SerializedMessage> events;

    /**
     * Whether the events in this batch should only be stored (not published to consumers).
     * <p>
     * This can be used to persist events purely for audit purposes or internal replay, without triggering downstream
     * handlers.
     */
    boolean storeOnly;

    /**
     * Returns {@code true} if the batch contains no events.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return events.isEmpty();
    }

    /**
     * Returns the number of events in this batch.
     */
    @JsonIgnore
    public int getSize() {
        return events.size();
    }

    /**
     * Calculates the total number of bytes in the data across all messages within the batch.
     */
    @JsonIgnore
    public long getBytes() {
        return events.stream().mapToLong(SerializedMessage::getBytes).sum();
    }

    /**
     * Returns a human-readable representation of this batch, including its aggregate ID and event count.
     */
    @Override
    public String toString() {
        return "EventBatch{" +
               "aggregateId='" + aggregateId + '\'' +
               ", event count=" + events.size() +
               ", storeOnly=" + storeOnly +
               '}';
    }

    /**
     * Transforms this batch into a lightweight metric representation for logging and monitoring.
     */
    @JsonIgnore
    public Metric toMetric() {
        return new Metric(aggregateId, getSize(), getBytes(), storeOnly);
    }

    /**
     * A compact metric representation of an {@link EventBatch}, used in {@link AppendEvents.Metric}.
     */
    @Value
    @Builder
    public static class Metric {
        /**
         * The identifier of the aggregate associated with this operation.
         * <p>
         * This field is commonly used to uniquely identify an entity in an event-sourced system, allowing operations
         * such as event retrieval, appending, or deletion to be scoped to the specific aggregate.
         */
        String aggregateId;

        /**
         * The size of the metric, representing the number of events or elements involved in a specific operation or in
         * the aggregate.
         */
        int size;

        /**
         * The total number of bytes in the data across all messages within the batch.
         */
        long bytes;

        /**
         * If {@code true}, the event batch only contained events stored in the event store, without publishing via
         * the global event log for processing by downstream handlers. If {@code false}, the events were stored and
         * logged in the global event log.
         */
        boolean storeOnly;
    }
}
