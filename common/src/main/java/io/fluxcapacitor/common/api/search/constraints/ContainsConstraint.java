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

import java.beans.ConstructorProperties;
import java.util.regex.Pattern;

import static io.fluxcapacitor.common.api.search.constraints.AllConstraint.all;
import static io.fluxcapacitor.common.api.search.constraints.AnyConstraint.any;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

@Value
public class ContainsConstraint extends PathConstraint {
    static final String letterOrNumber = "\\p{L}0-9";
    private static final Pattern splitPattern = Pattern.compile(String.format("(?<=[%1$s])\\*(?=[%1$s])", letterOrNumber));

    public static Constraint contains(@NonNull String phrase, String... paths) {
        String[] parts = splitPattern.split(phrase);
        if (parts.length < 2) {
            switch (paths.length) {
                case 0:
                    return new ContainsConstraint(phrase, null);
                case 1:
                    return new ContainsConstraint(phrase, paths[0]);
                default:
                    return new AnyConstraint(stream(paths).map(p -> new ContainsConstraint(phrase, p)).collect(
                            toList()));
            }
        } else {
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i] + "*";
                i++;
                parts[i] = "*" + parts[i];
            }
            return any(stream(paths).map(p -> all(
                    stream(parts).map(part -> new ContainsConstraint(part, p)).collect(toList()))).collect(toList()));
        }
    }

    @NonNull String contains;
    String path;
    boolean prefixSearch;
    boolean postfixSearch;

    @ConstructorProperties({"contains", "path"})
    public ContainsConstraint(String contains, String path) {
        contains = StringUtils.stripAccents(StringUtils.strip(requireNonNull(contains).toLowerCase()));
        boolean prefixSearch, postfixSearch;
        if (contains.endsWith("*")) {
            contains = contains.substring(0, contains.length() - 1);
            this.postfixSearch = true;
        } else {
            this.postfixSearch = false;
        }
        if (contains.startsWith("*")) {
            contains = contains.substring(1);
            this.prefixSearch = true;
        } else {
            this.prefixSearch = false;
        }
        this.contains = contains;
        this.path = path;
    }

    @Override
    protected boolean matches(Document.Entry entry) {
        return pattern().matcher(entry.asPhrase()).find();
    }

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Pattern pattern = Pattern.compile((prefixSearch ? "" :  String.format("(?<=[^%s]|\\A)", letterOrNumber)) + Pattern.quote(contains) + (postfixSearch ? "" : String.format("(?=[^%s]|\\Z)", letterOrNumber)));
}
