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

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LookAheadConstraint extends PathConstraint {

    public static Constraint lookAhead(String lookAhead, String... paths) {
        return isBlank(lookAhead) ? NoOpConstraint.instance : new LookAheadConstraint(lookAhead, List.of(paths));
    }

    @NonNull String lookAhead;
    @With List<String> paths;

    @Override
    public boolean matches(Document document) {
        return decompose().matches(document);
    }

    @Override
    public boolean hasTextConstraint() {
        return true;
    }

    @Override
    protected boolean matches(Document.Entry entry) {
        throw new UnsupportedOperationException();
    }

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decompose = contains(getLookAhead(), false, true, true, getPaths().toArray(String[]::new));

}
