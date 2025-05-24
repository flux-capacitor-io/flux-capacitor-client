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
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.experimental.Accessors;

import java.util.List;

import static io.fluxcapacitor.common.api.search.constraints.ContainsConstraint.contains;
import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * A constraint that supports search-as-you-type functionality by matching terms or phrases that start with a given
 * input string.
 * <p>
 * For example, the input {@code "hat"} will match documents containing terms like {@code "hatter"} or phrases like
 * {@code "the cat with the hat"}.
 * <p>
 * The constraint delegates matching to an equivalent {@link ContainsConstraint} created via {@link #decompose()}.
 * <p>
 * If no paths are provided, the constraint will match across all paths in the document.
 *
 * @see QueryConstraint
 * @see ContainsConstraint
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LookAheadConstraint extends PathConstraint {

    /**
     * Creates a {@code LookAheadConstraint} that matches any terms or phrases starting with the given input string.
     * <p>
     * If the input is blank or {@code null}, this returns a {@link NoOpConstraint}.
     *
     * @param lookAhead the search prefix
     * @param paths     optional field paths to restrict the search to
     * @return a {@code LookAheadConstraint} or {@link NoOpConstraint}
     */
    public static Constraint lookAhead(String lookAhead, String... paths) {
        return isBlank(lookAhead) ? NoOpConstraint.instance : new LookAheadConstraint(lookAhead, List.of(paths));
    }

    /**
     * The look-ahead string used for matching. This acts as a prefix for partial term or phrase matching.
     */
    @NonNull
    String lookAhead;

    /**
     * Field paths where the search should be applied. If no paths are specified, all fields are considered.
     */
    @With
    List<String> paths;


    /**
     * Evaluates whether this constraint matches the given {@link Document}. This implementation delegates to the result
     * of {@link #decompose()}.
     *
     * @param document the document to test
     * @return {@code true} if the document matches, otherwise {@code false}
     */
    @Override
    public boolean matches(Document document) {
        return decompose().matches(document);
    }

    /**
     * Not supported for this constraint type, as this constraint relies on higher-level document decomposition.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    protected boolean matches(Document.Entry entry) {
        throw new UnsupportedOperationException();
    }

    /**
     * Decomposes this constraint into an equivalent {@link ContainsConstraint} with:
     * <ul>
     *     <li>case-insensitive matching</li>
     *     <li>word and phrase start matching enabled</li>
     *     <li>value normalization applied</li>
     * </ul>
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    @Accessors(fluent = true)
    Constraint decompose = contains(getLookAhead(), false, true, true, getPaths().toArray(String[]::new));

}
