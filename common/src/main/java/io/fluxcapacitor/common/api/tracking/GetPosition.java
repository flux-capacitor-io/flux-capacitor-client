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

package io.fluxcapacitor.common.api.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Request;
import lombok.Value;

/**
 * Query to retrieve the current tracked {@link io.fluxcapacitor.common.api.tracking.Position} for a given
 * {@code consumer} and {@link MessageType}.
 * <p>
 * This is typically used for administrative, diagnostic, or monitoring purposes to inspect the progress of a
 * consumer group within a message stream.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Determine how far a consumer has progressed in processing a given message type</li>
 *   <li>Verify if a reset or store operation has taken effect</li>
 *   <li>Generate metrics or dashboards on consumer lag</li>
 * </ul>
 *
 * <p>
 * Note that this request will return the position as tracked by the Flux platform, regardless of any
 * in-memory state held by local trackers.
 *
 * @see io.fluxcapacitor.common.api.tracking.Position
 * @see StorePosition
 * @see ResetPosition
 */
@Value
public class GetPosition extends Request {

    /**
     * The type of messages being consumed (e.g., {@code EVENT}, {@code COMMAND}, etc.).
     */
    MessageType messageType;

    /**
     * The name of the consumer group whose position is being queried.
     */
    String consumer;
}
