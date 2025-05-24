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

import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A request to fetch stored events for a specific aggregate in an event-sourced system.
 * <p>
 * This class is used by the Flux platform to retrieve historical events from the event store associated with a given
 * aggregate. It supports pagination via {@code lastSequenceNumber} and {@code batchSize} to efficiently load large
 * event streams.
 *
 * <h2>Usage</h2>
 * Typically used when:
 * <ul>
 *   <li>Reconstructing an aggregate's state from its event history</li>
 *   <li>Streaming events for external use or replay</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * GetEvents request = new GetEvents("order-123", 50L, 100);
 * }</pre>
 *
 * @see io.fluxcapacitor.common.api.SerializedMessage
 * @see GetEventsResult
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class GetEvents extends Request {

    /**
     * The identifier of the aggregate whose events are to be retrieved.
     */
    String aggregateId;

    /**
     * The (exclusive) last known sequence number of events previously read.
     * <p>
     * The platform will return events with sequence numbers strictly greater than this value. Use {@code null} to
     * retrieve from the beginning.
     */
    Long lastSequenceNumber;

    /**
     * The maximum number of events to return in this request.
     */
    int batchSize;
}
