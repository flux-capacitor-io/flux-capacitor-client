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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.time.Instant;

/**
 * Use 48 bits of the current time in milliseconds since epoch as the base of the index. The remaining 16 bits (65k) are
 * used to increment the index if messages are written in the same ms as the last batch.
 * <p>
 * The index is only able to store 2^47 - 1 ms of time since epoch, i.e. about 4,500 years.
 * <p>
 * Uses {@link FluxCapacitor#currentClock()} to get the index corresponding to the current timestamp of Flux Capacitor's
 * internal clock.
 */
public class IndexUtils {

    public static long millisFromIndex(long index) {
        return index >> 16;
    }

    public static Instant timestampFromIndex(long index) {
        return Instant.ofEpochMilli(millisFromIndex(index));
    }

    public static long indexFromTimestamp(Instant timestamp) {
        return indexFromMillis(timestamp.toEpochMilli());
    }

    public static long indexFromMillis(long millisSinceEpoch) {
        return millisSinceEpoch << 16;
    }

    public static long maxIndexFromMillis(long millisSinceEpoch) {
        return indexFromMillis(millisSinceEpoch) + 65_536 - 1;
    }

    public static int offsetFromIndex(long index) {
        return (int) (index % 65_536);
    }

    public static long indexForCurrentTime() {
        return FluxCapacitor.currentClock().millis() << 16;
    }

    public static long nextIndex(Long lastIndex) {
        return lastIndex == null ? indexForCurrentTime() : Math.max(indexForCurrentTime(), lastIndex + 1L);
    }
}
