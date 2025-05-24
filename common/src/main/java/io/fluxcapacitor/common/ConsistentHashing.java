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

/**
 * Utility class for computing segment assignments using consistent hashing.
 * <p>
 * This class maps routing keys to numeric segments using a hash function, typically to support partitioned processing
 * or distribution across consumers in Flux.
 *
 * <p>
 * Segments are defined as integer ranges, and a typical range is from 0 (inclusive) to {@code MAX_SEGMENT} (exclusive),
 * where {@code MAX_SEGMENT} is defined in {@link Position}.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Computes deterministic segment for a given routing key</li>
 *   <li>Supports customizable hash functions</li>
 *   <li>Checks if a segment falls within a range</li>
 *   <li>Detects whether a segment range is empty</li>
 * </ul>
 */
public class ConsistentHashing {

    /**
     * Default hash function used to compute segment index from a routing key. Uses Murmur3 32-bit hash for fast and
     * stable hashing.
     */
    private static final Function<String, Integer> defaultHashFunction = Murmur3::murmurhash3_x86_32;

    /**
     * Computes the segment for a given routing key using the default hash function and the default maximum segment size.
     *
     * @param routingKey the routing key to hash
     * @return the segment index
     */
    public static int computeSegment(String routingKey) {
        return computeSegment(routingKey, defaultHashFunction, Position.MAX_SEGMENT);
    }

    /**
     * Computes the segment for a given routing key and maximum number of segments, using the default hash function.
     *
     * @param routingKey   the routing key to hash
     * @param maxSegments  the number of available segments
     * @return the segment index
     */
    public static int computeSegment(String routingKey, int maxSegments) {
        return computeSegment(routingKey, defaultHashFunction, maxSegments);
    }

    /**
     * Computes the segment using a custom hash function and max segment size.
     *
     * @param routingKey    the routing key to hash
     * @param hashFunction  a custom hash function
     * @param maxSegments   the number of available segments
     * @return the segment index
     */
    public static int computeSegment(String routingKey, Function<String, Integer> hashFunction, int maxSegments) {
        return Math.abs(hashFunction.apply(routingKey)) % maxSegments;
    }

    /**
     * Checks if the segment computed from the routing key falls within the specified segment range.
     *
     * @param routingKey    the routing key
     * @param segmentRange  an array of two integers [start, end)
     * @return {@code true} if the segment is within the range
     */
    public static boolean fallsInRange(String routingKey, int[] segmentRange) {
        return fallsInRange(computeSegment(routingKey), segmentRange);
    }

    /**
     * Same as {@link #fallsInRange(String, int[])}, but uses a custom max segment size.
     *
     * @param routingKey    the routing key
     * @param segmentRange  segment range [start, end)
     * @param maxSegments   number of available segments
     * @return {@code true} if the computed segment falls in the range
     */
    public static boolean fallsInRange(String routingKey, int[] segmentRange, int maxSegments) {
        return fallsInRange(computeSegment(routingKey, maxSegments), segmentRange);
    }

    /**
     * Checks if a given segment index falls within a specified segment range.
     *
     * @param segment       the segment index
     * @param segmentRange  segment range [start, end)
     * @return {@code true} if the segment is within the range
     */
    public static boolean fallsInRange(int segment, int[] segmentRange) {
        return segment >= segmentRange[0] && segment < segmentRange[1];
    }

    /**
     * Checks whether the segment range is empty (i.e., start == end).
     *
     * @param segmentRange an array of two integers [start, end)
     * @return {@code true} if the range is empty
     */
    public static boolean isEmptyRange(int[] segmentRange) {
        return segmentRange[0] == segmentRange[1];
    }

}
