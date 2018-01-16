/*
 * Copyright (c) 2016-2017 Flux Capacitor.
 *
 * Do not copy, cite or distribute without permission.
 */

package io.fluxcapacitor.common;

/**
 * Use 48 bits of the current time in milliseconds since epoch as the base of the index. The remaining 16 bits (65k) are
 * used to increment the index if messages are written in the same ms as the last batch.
 * <p>
 * The index is only able to store 2^47 - 1 ms of time since epoch, i.e. about 4,500 years.
 */
public class IndexUtils {

    public static long timeFromIndex(long index) {
        return index >> 16;
    }

    public static long indexFromTimestamp(long millisSinceEpoch) {
        return millisSinceEpoch << 16;
    }

    public static long indexForCurrentTime() {
        return System.currentTimeMillis() << 16;
    }

    public static int offsetFromIndex(long index) {
        return (int) (index % 65_536);
    }

}
