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
 * Result of a {@link io.fluxcapacitor.common.api.tracking.ClaimSegment} request.
 * <p>
 * This response confirms that a tracker has successfully claimed a segment range for client-controlled tracking.
 * It provides the current {@link Position} and the claimed segment range, allowing the client to manage its own
 * message consumption logic.
 *
 * @see io.fluxcapacitor.common.api.tracking.ClaimSegment
 * @see io.fluxcapacitor.common.api.tracking.Position
 */
@Value
public class ClaimSegmentResult implements RequestResult {

    /**
     * The ID of the original {@link io.fluxcapacitor.common.api.tracking.ClaimSegment} request.
     */
    long requestId;

    /**
     * The current {@link Position} containing last seen indices for each segment, as known by the Flux platform.
     * <p>
     * This position allows clients to avoid duplicate processing or gaps by inspecting the last seen index
     * for the claimed segment.
     */
    Position position;

    /**
     * The claimed segment range, expressed as an inclusive-exclusive {@code [start, end)} array.
     * <p>
     * Trackers may use this segment to filter or balance work across multiple instances.
     */
    int[] segment;

    /**
     * The system time (epoch milliseconds) when the segment claim was created.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Returns a compact metric representation of this claim for monitoring purposes.
     */
    @Override
    public Object toMetric() {
        return new Metric(requestId, position.lowestIndexForSegment(segment).orElse(null), segment, timestamp);
    }

    /**
     * Lightweight metric representation of a {@link ClaimSegmentResult}, used for telemetry.
     */
    @Value
    public static class Metric {
        long requestId;
        Long lastIndex;
        int[] segment;
        long timestamp;
    }
}
