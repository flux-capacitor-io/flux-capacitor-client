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

import java.util.List;
import java.util.regex.Pattern;

import static io.fluxcapacitor.common.SearchUtils.letterOrNumber;
import static io.fluxcapacitor.common.SearchUtils.normalize;
import static io.fluxcapacitor.common.SearchUtils.splitInTerms;
import static java.util.stream.Collectors.toList;

/**
 * A constraint that matches document entries containing the specified phrase, with optional wildcard-like flexibility.
 *
 * <p>This constraint can simulate different types of text searches based on the configuration:
 *
 * <ul>
 *     <li>{@code prefixSearch = true}:
 *         Matches entries that end with the phrase (e.g., {@code *day} matches "Monday", "Tuesday").</li>
 *     <li>{@code postfixSearch = true}:
 *         Matches entries that start with the phrase (e.g., {@code mon*} matches "Monday", "Monster").</li>
 *     <li>Both {@code prefixSearch} and {@code postfixSearch}:
 *         Matches entries that contain the phrase anywhere (e.g., {@code *mon*} matches "Monday", "Common", "Commoner").</li>
 *     <li>Both {@code prefixSearch} and {@code postfixSearch} disabled (default):
 *         Matches the full word exactly as it appears (normalized and case-insensitive).</li>
 * </ul>
 *
 * <p>If {@code splitInTerms} is true, the input phrase is split into individual normalized terms, and each term is
 * matched independently using the same search behavior. All terms must be found in the same document entry for the
 * match to succeed.
 * <p>
 * Matching is performed against the normalized form of each document entry using regular expressions if filtering is
 * done in-memory. When using Flux Platform, this constraint is evaluated directly within the backing data store
 * whenever possible.
 *
 * <p>If no paths are provided, the constraint matches all paths within the document.</p>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *     <li>
 *         Terms using a <strong>prefix</strong> or <strong>postfix</strong> wildcard (e.g., {@code user*} or {@code *name}) are resolved at the data store level and typically fast.
 *     </li>
 *     <li>
 *         Terms using <strong>both prefix and postfix</strong> wildcards (e.g., {@code *log*}) are not natively supported by the backend and require <strong>in-memory filtering</strong>, which can be slow for large result sets.
 *     </li>
 *     <li>
 *         <strong>Similarly, prefix or postfix matches with terms shorter than 3 characters</strong> are also evaluated in-memory.
 *         <br>
 *         <em>Note:</em> This limitation does <strong>not</strong> apply to complete word matches (i.e., without wildcards).
 *         For example, {@code "to"} as an exact term is efficient, but {@code to*} or {@code *to} are not.
 *     </li>
 *     <li>
 *         When possible, prefer longer terms and structure queries to enable efficient execution via the underlying document store.
 *     </li>
 * </ul>
 *
 * @see PathConstraint
 * @see LookAheadConstraint
 * @see QueryConstraint
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContainsConstraint extends PathConstraint {

    /**
     * Create a basic {@code ContainsConstraint} that checks if the given phrase exists anywhere in the entry, using
     * default full-word matching (i.e., neither prefix nor postfix logic).
     *
     * @param phrase the phrase to match
     * @param paths  optional field paths to restrict the search to
     * @return a {@code Constraint} that matches if the phrase is found
     */
    public static Constraint contains(String phrase, String... paths) {
        return contains(phrase, false, false, false, paths);
    }

    /**
     * Create a constraint that allows prefix and/or postfix matching of a given phrase.
     * <p>
     * This provides fine-grained control over how terms are matched:
     * <ul>
     *   <li>{@code prefixSearch = true}: allows matches at the start of words</li>
     *   <li>{@code postfixSearch = true}: allows matches at the end of words</li>
     * </ul>
     *
     * @param phrase        the phrase to match
     * @param prefixSearch  whether to allow entries that end with the phrase, e.g.: *hat
     * @param postfixSearch whether to allow entries that start with the phrase, e.g.: hat*
     * @param paths         optional field paths to restrict the match to
     * @return a {@code Constraint} using the specified match settings
     */
    public static Constraint contains(String phrase, boolean prefixSearch, boolean postfixSearch,
                                      String... paths) {
        return contains(phrase, prefixSearch, postfixSearch, false, paths);
    }

    /**
     * Create a constraint that optionally splits the input phrase into individual terms and combines the resulting
     * constraints using {@link AllConstraint}.
     * <p>
     * This is especially useful when you want all words in a multi-word search query to be matched.
     *
     * @param phrase        the full phrase to optionally split and match
     * @param prefixSearch  whether to allow entries that end with the phrase, e.g.: *hat
     * @param postfixSearch whether to allow entries that start with the phrase, e.g.: hat*
     * @param splitInTerms  if {@code true}, splits the phrase into individual normalized terms
     * @param paths         optional field paths to restrict the match
     * @return a compound {@link AllConstraint} if multiple terms, or a single {@code ContainsConstraint}
     */
    public static Constraint contains(String phrase, boolean prefixSearch, boolean postfixSearch,
                                      boolean splitInTerms, String... paths) {
        return phrase == null ? NoOpConstraint.instance : AllConstraint.all(
                (splitInTerms ? splitInTerms(normalize(phrase)) : List.of(normalize(phrase))).stream()
                        .map(term -> new ContainsConstraint(term, List.of(paths), prefixSearch, postfixSearch))
                        .collect(toList()));
    }

    /**
     * The normalized string to match within document entries.
     */
    @NonNull
    String contains;

    /**
     * The list of paths in the document to restrict matching to. If left empty, the match applies to all paths.
     */
    @With
    List<String> paths;

    /**
     * If {@code true}, matches any word or phrase that ends with the given term.
     * <p>For example, with {@code contains = "day"}, this would match "Monday", "Tuesday", etc.
     * <p>Use this to simulate a {@code *term} search.
     */
    boolean prefixSearch;

    /**
     * If {@code true}, matches any word or phrase that starts with the given term.
     * <p>For example, with {@code contains = "mon"}, this would match "Monday", "Monster", etc.
     * <p>Use this to simulate a {@code term*} search.
     */
    boolean postfixSearch;

    /**
     * Checks whether a single document entry matches the constraint. The entry is converted to a normalized phrase
     * string, and the compiled regular expression is applied.
     *
     * @param entry the document entry
     * @return {@code true} if the entry matches the pattern, otherwise {@code false}
     */
    @Override
    protected boolean matches(Document.Entry entry) {
        return pattern().matcher(entry.asPhrase()).find();
    }

    /**
     * A compiled regex pattern used to match terms inside the normalized document content.
     * <p>
     * This pattern is lazily constructed using the normalized form of {@link #getContains()}, and it respects the
     * {@link #prefixSearch} and {@link #postfixSearch} flags to anchor matching at term boundaries.
     */
    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Pattern pattern = Pattern.compile(
            (prefixSearch ? "" : String.format("(?<=[^%s]|\\A)", letterOrNumber)) + Pattern.quote(
                    normalize(contains)) + (postfixSearch ? "" :
                    String.format("(?=[^%s]|\\Z)", letterOrNumber)));
}
