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

import io.fluxcapacitor.common.api.RequestResult;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

/**
 * Response to a {@link GetEvents} request, returning a batch of events for an aggregate.
 * <p>
 * This class is used by the Flux platform to deliver a segment of the event stream associated with an aggregate. It
 * includes metadata for sequencing and tracking purposes.
 *
 * <p>
 * Events are returned in an {@link EventBatch}, which includes the aggregate ID, a list of {@link SerializedMessage}s,
 * and a flag indicating whether the events were stored-only or also published.
 *
 * @see GetEvents
 * @see EventBatch
 * @see SerializedMessage
 */
@Value
public class GetEventsResult implements RequestResult {

    /**
     * A unique identifier that correlates this result with its {@link GetEvents} request.
     */
    long requestId;

    /**
     * The batch of events returned for the requested aggregate.
     */
    EventBatch eventBatch;

    /**
     * The sequence number of the last event included in this batch.
     * <p>
     * This is useful for paging and requesting the next batch of events.
     */
    long lastSequenceNumber;

    /**
     * Timestamp at which this result was generated.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Converts this result to a minimal metric object for performance logging or monitoring.
     * <p>
     * This avoids logging full event payloads while preserving key metadata.
     *
     * @return a metric summary of the response
     */
    @Override
    public Metric toMetric() {
        return new Metric(eventBatch.toMetric(), lastSequenceNumber, timestamp);
    }

    /**
     * Lightweight metric representation of a {@link GetEventsResult} used for monitoring.
     */
    @Value
    public static class Metric {
        /**
         * The metric representation of the returned {@link EventBatch}.
         */
        EventBatch.Metric eventBatch;

        /**
         * The last sequence number of the events included in this result.
         */
        long lastSequenceNumber;

        /**
         * The time this metric was generated.
         */
        long timestamp;
    }
}