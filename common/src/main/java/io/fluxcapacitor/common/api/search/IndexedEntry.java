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

package io.fluxcapacitor.common.api.search;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.search.Document.Path;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.With;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;

import static io.fluxcapacitor.common.SearchUtils.ISO_FULL;

@Value
@AllArgsConstructor
public class IndexedEntry implements Comparable<IndexedEntry> {
    private static final int DECIMALS = 6;
    private static final BigDecimal SCALE = BigDecimal.TEN.pow(DECIMALS);
    private static final BigInteger MIN_ENCODED = BigInteger.ZERO;
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_ENCODED = MAX_LONG.subtract(MIN_LONG).multiply(SCALE.toBigInteger()); // Full positive range
    private static final BigInteger OFFSET = MAX_LONG.multiply(SCALE.toBigInteger()); // Offset to shift negative range
    private static final BigInteger SCALED_MIN = MIN_LONG.multiply(SCALE.toBigInteger());
    private static final int PAD_WIDTH = MAX_ENCODED.toString().length();

    public static final Comparator<IndexedEntry> comparator = Comparator.comparing(IndexedEntry::getName)
            .thenComparing(IndexedEntry::getValue, String::compareTo);

    @With
    String name;
    String value;

    public IndexedEntry(String name, Object value) {
        this(name, formatSortable(value));
    }

    public static String formatSortable(Object value) {
        return switch (value) {
            case null -> "";
            case Number n -> toSortableString(n);
            case Instant instant -> ISO_FULL.format(instant);
            default -> SearchUtils.normalize(value.toString());
        };
    }

    @Getter(lazy = true)
    @JsonIgnore
    Path path = new Path(name);

    @Override
    public int compareTo(@NotNull IndexedEntry o) {
        return comparator.compare(this, o);
    }

    public static String toSortableString(Number number) {
        BigInteger scaled = toBigDecimal(number).setScale(DECIMALS, RoundingMode.HALF_UP).multiply(SCALE)
                .toBigInteger().max(MIN_LONG).min(MAX_LONG).subtract(SCALED_MIN);
        return String.format("%0" + PAD_WIDTH + "d", scaled);
    }

    static BigDecimal toBigDecimal(Number number) {
        return switch (number) {
            case BigDecimal bd -> bd;
            case Integer i -> new BigDecimal(i);
            case Long i -> new BigDecimal(i);
            case BigInteger bi -> new BigDecimal(bi);
            default -> BigDecimal.valueOf(number.doubleValue());
        };
    }
}
