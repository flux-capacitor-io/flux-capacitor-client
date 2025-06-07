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

/**
 * Represents a sortable entry in a {@link io.fluxcapacitor.common.search.Document} for use in search
 * operations.
 * <p>
 * {@code SortableEntry} values are primarily used to support efficient sorting and range filtering in the document store
 * (e.g., numeric or timestamp-based queries). Each entry consists of a name (field path) and a pre-normalized,
 * lexicographically sortable value as a {@code String}.
 *
 * <p>
 * Values are formatted using a normalization strategy depending on their type:
 * <ul>
 *   <li><strong>Numbers</strong>: Encoded into padded, base-10 fixed-width strings for fast and correct range comparisons</li>
 *   <li><strong>Timestamps</strong>: Formatted using {@link io.fluxcapacitor.common.SearchUtils#ISO_FULL}</li>
 *   <li><strong>Other values</strong>: Normalized to lowercase using {@link io.fluxcapacitor.common.SearchUtils#normalize(String)}</li>
 * </ul>
 *
 * <h2>Example Use Cases</h2>
 * <ul>
 *   <li>Sorting search results by timestamp or numeric score</li>
 *   <li>Enabling fast {@link io.fluxcapacitor.common.api.search.constraints.BetweenConstraint} evaluation</li>
 *   <li>Filtering or grouping based on normalized values</li>
 * </ul>
 *
 * @see io.fluxcapacitor.common.search.Document
 * @see io.fluxcapacitor.common.api.search.constraints.BetweenConstraint
 */
@Value
@AllArgsConstructor
public class SortableEntry implements Comparable<SortableEntry> {

    /**
     * Number of decimal places for encoding numeric values.
     */
    private static final int DECIMALS = 6;

    /**
     * Scaling factor for preserving decimal precision.
     */
    private static final BigDecimal SCALE = BigDecimal.TEN.pow(DECIMALS);
    private static final BigInteger MIN_ENCODED = BigInteger.ZERO;
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final BigInteger MIN_LONG = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger MAX_ENCODED = MAX_LONG.subtract(MIN_LONG).multiply(SCALE.toBigInteger()); // Full positive range
    private static final BigInteger OFFSET = MAX_LONG.multiply(SCALE.toBigInteger()); // Offset to shift negative range
    private static final BigInteger SCALED_MIN = MIN_LONG.multiply(SCALE.toBigInteger());
    private static final int PAD_WIDTH = MAX_ENCODED.toString().length();

    /**
     * Comparator used to lexicographically compare entries based on {@link #name} and {@link #value}.
     */
    public static final Comparator<SortableEntry> comparator = Comparator.comparing(SortableEntry::getName)
            .thenComparing(SortableEntry::getValue, String::compareTo);

    /**
     * The name or path of the field (e.g., {@code "price"}, {@code "timestamp"}).
     */
    @With
    String name;

    /**
     * The encoded and normalized value used for sorting or comparison.
     */
    String value;

    /**
     * Constructs a new {@code SortableEntry} by formatting the given object into a normalized, sortable string.
     *
     * @param name  the field name or path
     * @param value the raw object value (e.g., {@link Number}, {@link Instant}, or {@code String})
     */
    public SortableEntry(String name, Object value) {
        this(name, formatSortable(value));
    }

    /**
     * Formats the given object into a sortable string depending on its type.
     * <ul>
     *   <li>For {@link Number}s: Encoded as zero-padded base-10</li>
     *   <li>For {@link Instant}s: Formatted as ISO-8601 string</li>
     *   <li>Other objects: Normalized string representation</li>
     * </ul>
     */
    public static String formatSortable(Object value) {
        return switch (value) {
            case null -> "";
            case Number n -> toSortableString(n);
            case Instant instant -> ISO_FULL.format(instant);
            default -> SearchUtils.normalize(value.toString());
        };
    }

    /**
     * Lazily computed {@link Path} corresponding to the entry name.
     */
    @Getter(lazy = true)
    @JsonIgnore
    Path path = new Path(name);

    @Override
    public int compareTo(@NotNull SortableEntry o) {
        return comparator.compare(this, o);
    }

    /**
     * Converts a {@link Number} to a lexicographically sortable zero-padded string.
     */
    public static String toSortableString(Number number) {
        BigInteger scaled = toBigDecimal(number).setScale(DECIMALS, RoundingMode.HALF_UP).multiply(SCALE)
                .toBigInteger().max(MIN_LONG).min(MAX_LONG).subtract(SCALED_MIN);
        return String.format("%0" + PAD_WIDTH + "d", scaled);
    }

    /**
     * Converts a {@link Number} to {@link BigDecimal}, handling different numeric types explicitly.
     */
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
