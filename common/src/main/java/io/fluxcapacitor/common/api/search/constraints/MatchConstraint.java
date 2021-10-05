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

import io.fluxcapacitor.common.SearchUtils;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

@Value
public class MatchConstraint extends PathConstraint {
    public static Constraint match(Object value, String... paths) {
        return match(value, true, paths);
    }

    public static Constraint match(Object value, boolean ignoreCaseAndAccents, String... paths) {
        switch (paths.length) {
            case 0: return matchForPath(value, null, ignoreCaseAndAccents);
            case 1: return matchForPath(value, paths[0], ignoreCaseAndAccents);
            default: return new AnyConstraint(Arrays.stream(paths).map(p -> matchForPath(value, p, ignoreCaseAndAccents)).collect(toList()));
        }
    }

    protected static Constraint matchForPath(Object value, String path, boolean ignoreCaseAndAccents) {
        if (value instanceof Collection<?>) {
            List<Constraint> constraints =
                    ((Collection<?>) value).stream().filter(Objects::nonNull)
                            .map(v -> new MatchConstraint(v.toString(), path, ignoreCaseAndAccents))
                            .collect(toList());
            switch (constraints.size()) {
                case 0: return noOp;
                case 1: return constraints.get(0);
                default: return new AnyConstraint(constraints);
            }
        } else {
            return value == null ? noOp : new MatchConstraint(value.toString(), path, ignoreCaseAndAccents);
        }
    }

    @NonNull String match;
    String path;
    boolean ignoreCaseAndAccents;

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    @Accessors(fluent = true)
    @EqualsAndHashCode.Exclude
    Predicate<Document.Entry> entryMatcher = computeEntryMatcher();

    @Override
    protected boolean matches(Document.Entry entry) {
        return entryMatcher().test(entry);
    }

    protected Predicate<Document.Entry> computeEntryMatcher() {
        if (ignoreCaseAndAccents) {
            String pattern = SearchUtils.normalize(getMatch());
            return entry -> entry.asPhrase().equals(pattern);
        }
        return entry -> entry.getValue().equals(match);
    }
}
