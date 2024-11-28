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

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContainsConstraint extends PathConstraint {

    public static Constraint contains(String phrase, String... paths) {
        return contains(phrase, false, false, false, paths);
    }

    public static Constraint contains(String phrase, boolean prefixSearch, boolean postfixSearch,
                                      String... paths) {
        return contains(phrase, prefixSearch, postfixSearch, false, paths);
    }

    public static Constraint contains(String phrase, boolean prefixSearch, boolean postfixSearch,
                                      boolean splitInTerms, String... paths) {
        return phrase == null ? NoOpConstraint.instance : AllConstraint.all(
                (splitInTerms ? splitInTerms(normalize(phrase)) : List.of(normalize(phrase))).stream()
                        .map(term -> new ContainsConstraint(term, List.of(paths), prefixSearch, postfixSearch))
                        .collect(toList()));
    }

    @NonNull String contains;
    @With
    List<String> paths;
    boolean prefixSearch;
    boolean postfixSearch;

    @Override
    protected boolean matches(Document.Entry entry) {
        return pattern().matcher(entry.asPhrase()).find();
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Pattern pattern = Pattern.compile(
            (prefixSearch ? "" : String.format("(?<=[^%s]|\\A)", letterOrNumber)) + Pattern.quote(
                    normalize(contains)) + (postfixSearch ? "" :
                    String.format("(?=[^%s]|\\Z)", letterOrNumber)));
}
