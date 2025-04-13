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

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BetweenConstraint extends PathConstraint {

    public static BetweenConstraint between(Object min, Object maxExclusive, @NonNull String path) {
        return new BetweenConstraint(formatConstraintValue(min), formatConstraintValue(maxExclusive), List.of(path));
    }

    public static BetweenConstraint atLeast(@NonNull Object min, @NonNull String path) {
        return new BetweenConstraint(formatConstraintValue(min), null, List.of(path));
    }

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

    Object min;
    Object max;
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
