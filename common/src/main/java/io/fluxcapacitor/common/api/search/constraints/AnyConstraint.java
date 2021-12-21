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
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnyConstraint implements Constraint {
    public static Constraint any(Constraint... constraints) {
        return any(Arrays.asList(constraints));
    }

    public static Constraint any(Collection<Constraint> constraints) {
        var list = constraints.stream().distinct().collect(toList());
        switch (list.size()) {
            case 0: return NoOpConstraint.instance;
            case 1: return list.get(0);
            default: return new AnyConstraint(list);
        }
    }

    List<Constraint> any;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decompose = new AnyConstraint(getAny().stream().map(Constraint::decompose).collect(toList()));

    @Override
    public boolean matches(Document document) {
        return any.stream().anyMatch(c -> c.matches(document));
    }

    @Override
    public boolean hasPathConstraint() {
        return any.stream().anyMatch(Constraint::hasPathConstraint);
    }

}
