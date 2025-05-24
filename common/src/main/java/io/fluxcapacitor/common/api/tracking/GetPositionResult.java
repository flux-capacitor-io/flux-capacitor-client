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

import io.fluxcapacitor.common.api.RequestResult;
import lombok.Value;

/**
 * Response to a {@link GetPosition} request, containing the current tracked position for a specific consumer and
 * {@link io.fluxcapacitor.common.MessageType}.
 * <p>
 * This result provides insight into how far a consumer has progressed in processing messages. The {@link Position}
 * includes one or more segment-index pairs that together describe the consumption state of a consumer group.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Monitor consumer lag across distributed segments</li>
 *   <li>Confirm the effects of a {@link ResetPosition} or {@link StorePosition}</li>
 *   <li>Diagnose stuck or lagging consumers by examining last processed indexes</li>
 * </ul>
 *
 * @see GetPosition
 * @see Position
 * @see ResetPosition
 * @see StorePosition
 */
@Value
public class GetPositionResult implements RequestResult {

    /**
     * The unique identifier of the original {@link GetPosition} request.
     */
    long requestId;

    /**
     * The reported position of the consumer across message segments.
     */
    Position position;

    /**
     * The system timestamp at which this response was generated.
     */
    long timestamp = System.currentTimeMillis();
}
