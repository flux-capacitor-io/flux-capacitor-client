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

import io.fluxcapacitor.common.api.QueryResult;
import lombok.Value;

@Value
public class ClaimSegmentResult implements QueryResult {
    long requestId;
    Position position;
    int[] segment;
    long timestamp = System.currentTimeMillis();

    @Override
    public Object toMetric() {
        return new Metric(requestId, position.lowestIndexForSegment(segment).orElse(null), segment, timestamp);
    }

    @Value
    public static class Metric {
        long requestId;
        Long lastIndex;
        int[] segment;
        long timestamp;
    }
}
