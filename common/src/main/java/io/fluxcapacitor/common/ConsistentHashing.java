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

package io.fluxcapacitor.common;

import io.fluxcapacitor.common.api.tracking.Position;

import java.util.function.Function;

public class ConsistentHashing {

    private static final Function<String, Integer> defaultHashFunction = Murmur3::murmurhash3_x86_32;

    public static int computeSegment(String routingKey) {
        return computeSegment(routingKey, defaultHashFunction, Position.MAX_SEGMENT);
    }

    public static int computeSegment(String routingKey, int maxSegments) {
        return computeSegment(routingKey, defaultHashFunction, maxSegments);
    }

    public static int computeSegment(String routingKey, Function<String, Integer> hashFunction, int maxSegments) {
        return Math.abs(hashFunction.apply(routingKey)) % maxSegments;
    }

    public static boolean fallsInRange(String routingKey, int[] segmentRange) {
        return fallsInRange(computeSegment(routingKey), segmentRange);
    }

    public static boolean fallsInRange(String routingKey, int[] segmentRange, int maxSegments) {
        return fallsInRange(computeSegment(routingKey, maxSegments), segmentRange);
    }

    public static boolean fallsInRange(int segment, int[] segmentRange) {
        return segment >= segmentRange[0] && segment < segmentRange[1];
    }

    public static boolean isEmptyRange(int[] segmentRange) {
        return segmentRange[0] == segmentRange[1];
    }

}
