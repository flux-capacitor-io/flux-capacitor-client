/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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
import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

@Value
public class ContainsConstraint extends PathConstraint {
    static final String letterOrNumber = "\\p{L}0-9";

    public static Constraint contains(@NonNull String phrase, boolean prefixSearch, boolean postfixSearch, String... paths) {
        String normalized = normalize(phrase);
        switch (paths.length) {
            case 0:
                return new ContainsConstraint(normalized, null, prefixSearch, postfixSearch);
            case 1:
                return new ContainsConstraint(normalized, paths[0], prefixSearch, postfixSearch);
            default:
                return new AnyConstraint(stream(paths).map(p -> new ContainsConstraint(normalized, p, prefixSearch, postfixSearch)).collect(
                        toList()));
        }
    }

    public static String normalize(String phrase) {
        return StringUtils.stripAccents(StringUtils.strip(requireNonNull(phrase).toLowerCase()));
    }

    @NonNull String contains;
    String path;
    boolean prefixSearch;
    boolean postfixSearch;

    @Override
    protected boolean matches(Document.Entry entry) {
        return pattern().matcher(entry.asPhrase()).find();
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Pattern pattern = Pattern.compile((prefixSearch ? "" : String.format("(?<=[^%s]|\\A)", letterOrNumber)) + Pattern.quote(contains) + (postfixSearch ? "" : String.format("(?=[^%s]|\\Z)", letterOrNumber)));
}
