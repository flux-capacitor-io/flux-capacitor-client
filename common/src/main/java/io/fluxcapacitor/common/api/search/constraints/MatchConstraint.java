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

/**
 * A constraint that matches indexed document values based on text equality or normalized phrase matching.
 * <p>
 * The {@code MatchConstraint} is commonly used to filter documents that contain a specific word, phrase, or exact value
 * in one or more indexed fields.
 * </p>
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Match documents where the "status" field is "open"
 * Constraint constraint = MatchConstraint.match("open", "status");
 *
 * // Match across all indexed fields
 * Constraint constraint = MatchConstraint.match("open");
 *
 * // Match across multiple fields with strict equality
 * Constraint constraint = MatchConstraint.match("PENDING", true, "status", "state");
 * }</pre>
 *
 * <h2>Path Handling</h2>
 * You can provide one or more paths to target specific fields in the document. If no paths are provided, the constraint
 * will match anywhere in the document. Empty or null paths are filtered out automatically.
 *
 * <h2>Matching Modes</h2>
 * The constraint supports two modes:
 * <ul>
 *     <li>{@code strict = true} – uses exact string equality against the raw value of the entry.</li>
 *     <li>{@code strict = false} (default) – uses normalized matching via {@link SearchUtils#normalize(String)}
 *         and compares to the {@code asPhrase()} form of the entry (used for full-text search).</li>
 * </ul>
 *
 * @see PathConstraint
 * @see AnyConstraint
 * @see SearchUtils#normalize(String)
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MatchConstraint extends PathConstraint {

    /**
     * Creates a constraint that performs non-strict (normalized) matching of the provided value across the specified
     * paths.
     * <p>
     * If no paths are given, the match will be applied across all indexed fields in the document.
     * <p>
     * If the value is {@code null}, returns a {@link NoOpConstraint}. If the value is a {@link Collection}, creates a
     * disjunction ({@link AnyConstraint}) of match constraints for each non-null element. If the value implements
     * {@link HasId}, the {@code getId()} value is used for matching. For all other types, the value is converted to a
     * string.
     *
     * @param value the value to match
     * @param paths the paths to search in (or empty for all fields)
     * @return a {@link Constraint} instance or {@link NoOpConstraint}
     */
    public static Constraint match(Object value, String... paths) {
        return match(value, false, paths);
    }

    /**
     * Creates a constraint that matches the given value across the specified paths, with an option to enforce strict
     * string equality.
     * <p>
     * If no paths are given, the match will be applied across all indexed fields in the document.
     * <p>
     * If the value is {@code null}, returns a {@link NoOpConstraint}. If the value is a {@link Collection}, creates a
     * disjunction ({@link AnyConstraint}) of match constraints for each non-null element. If the value implements
     * {@link HasId}, the {@code getId()} value is used for matching. For all other types, the value is converted to a
     * string.
     *
     * @param value  the value to match
     * @param strict if {@code true}, use exact string matching; if {@code false}, use normalized phrase match
     * @param paths  the paths to search in (or empty for all fields)
     * @return a {@link Constraint} instance or {@link NoOpConstraint}
     */
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
