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

import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.HasId;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.NoOpConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MatchConstraint extends PathConstraint {
    public static Constraint match(Object value, String... paths) {
        return match(value, false, paths);
    }

    public static Constraint match(Object value, boolean strict, String... paths) {
        var filteredPaths = Arrays.stream(paths).filter(p -> p != null && !p.isBlank()).toList();
        switch (value) {
            case Collection<?> objects -> {
                List<Constraint> constraints =
                        objects.stream().filter(Objects::nonNull)
                                .map(v -> new MatchConstraint(v.toString(), filteredPaths, strict))
                                .collect(toList());
                return switch (constraints.size()) {
                    case 0 -> NoOpConstraint.instance;
                    case 1 -> constraints.getFirst();
                    default -> AnyConstraint.any(constraints);
                };
            }
            case HasId id -> {
                return new MatchConstraint(id.getId(), filteredPaths, strict);
            }
            case null -> {
                return NoOpConstraint.instance;
            }
            default -> {
                return new MatchConstraint(value.toString(), filteredPaths, strict);
            }
        }
    }

    @NonNull String match;
    @With List<String> paths;
    boolean strict;

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Predicate<Document.Entry> entryMatcher = computeEntryMatcher();

    @Override
    protected boolean matches(Document.Entry entry) {
        return entryMatcher().test(entry);
    }

    protected Predicate<Document.Entry> computeEntryMatcher() {
        if (strict) {
            return entry -> entry.getValue().equals(match);
        }
        String pattern = SearchUtils.normalize(getMatch());
        return entry -> entry.asPhrase().equals(pattern);
    }
}
