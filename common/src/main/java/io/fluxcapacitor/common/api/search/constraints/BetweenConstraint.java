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

package io.fluxcapacitor.common.api.search.constraints;

import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.common.search.Document.EntryType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.SearchUtils.ISO_FULL;

/**
 * A {@link Constraint} that filters documents based on whether a value in a specific path lies between two bounds.
 * <p>
 * The range is defined as:
 * <ul>
 *   <li>Inclusive of the lower bound (i.e. {@code >= min})</li>
 *   <li>Exclusive of the upper bound (i.e. {@code < max})</li>
 * </ul>
 * Either bound may be omitted to create an open-ended constraint.
 * <p>
 * Values are compared lexically unless they are numeric (e.g. {@link Number}) or temporal (e.g. {@link java.time.Instant}),
 * in which case they are normalized and compared accordingly.
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * // Matches documents where "price" is between 100 (inclusive) and 200 (exclusive)
 * Constraint c1 = BetweenConstraint.between(100, 200, "price");
 *
 * // Matches documents where "age" is at least 18
 * Constraint c2 = BetweenConstraint.atLeast(18, "age");
 *
 * // Matches documents with timestamps before a certain date
 * Constraint c3 = BetweenConstraint.below(Instant.now(), "createdAt");
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BetweenConstraint extends PathConstraint {

    /**
     * Creates a constraint that matches values in the given path that are greater than or equal to {@code min} and less
     * than {@code max}.
     *
     * @param min          the inclusive lower bound
     * @param maxExclusive the exclusive upper bound
     * @param path         the path in the document to compare
     */
    public static BetweenConstraint between(Object min, Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(formatConstraintValue(min), formatConstraintValue(maxExclusive), List.of(path));
    }

    /**
     * Creates a constraint that matches values in the given path that are greater than or equal to {@code min}.
     *
     * @param min  the inclusive lower bound
     * @param path the path in the document to compare
     */
    public static BetweenConstraint atLeast(@NonNull Object min, @NonNull String path) {
        return new BetweenConstraint(formatConstraintValue(min), null, List.of(path));
    }

    /**
     * Creates a constraint that matches values in the given path that are less than {@code maxExclusive}.
     *
     * @param maxExclusive the exclusive upper bound
     * @param path         the path in the document to compare
     */
    public static BetweenConstraint below(@NonNull Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(null, formatConstraintValue(maxExclusive), List.of(path));
    }

    static Object formatConstraintValue(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> ISO_FULL.format(instant);
            case Number number -> number;
            default -> value.toString().toLowerCase();
        };
    }

    /**
     * Represents the inclusive lower bound for a constraint. This value defines the minimum value that must be matched
     * by a given entity or document entry during the evaluation of the constraint.
     */
    Object min;

    /**
     * Represents the exclusive upper bound for a constraint in which values must be less than this maximum to meet the
     * criteria.
     */
    Object max;

    /**
     * The paths in the document to which the constraint applies. Each path represents a specific location or key in the
     * document structure, allowing the constraint to be evaluated at those locations.
     */
    @With
    List<String> paths;

    @Override
    protected boolean matches(Document.Entry entry) {
        return matcher().test(entry);
    }

    @Override
    protected boolean checkPathBeforeEntry() {
        return true;
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Predicate<Document.Entry> matcher = computeEntryPredicate();

    Predicate<Document.Entry> computeEntryPredicate() {
        var minEntry = asEntry(min);
        var maxEntry = asEntry(max);
        return min == null ? max == null ? s -> true : s -> s.compareTo(maxEntry) < 0 : max == null
                ? s -> s.compareTo(minEntry) >= 0 : s -> s.compareTo(minEntry) >= 0 && s.compareTo(maxEntry) < 0;
    }

    static Document.Entry asEntry(Object input) {
        return input == null ? null
                : new Document.Entry(input instanceof Number ? EntryType.NUMERIC : EntryType.TEXT, input.toString());
    }
}
