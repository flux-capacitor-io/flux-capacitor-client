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
public class AllConstraint implements Constraint {

    public static Constraint all(Constraint... constraints) {
        return all(Arrays.asList(constraints));
    }

    public static Constraint all(Collection<Constraint> constraints) {
        var list = constraints.stream().distinct().collect(toList());
        return switch (list.size()) {
            case 0 -> NoOpConstraint.instance;
            case 1 -> list.get(0);
            default -> new AllConstraint(list);
        };
    }

    List<Constraint> all;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decompose = AllConstraint.all(getAll().stream().map(Constraint::decompose).collect(toList()));

    @Override
    public boolean matches(Document document) {
        return all.stream().allMatch(c -> c.matches(document));
    }

    @Override
    public boolean hasPathConstraint() {
        return all.stream().anyMatch(Constraint::hasPathConstraint);
    }

    @Override
    public boolean hasTextConstraint() {
        return all.stream().anyMatch(Constraint::hasTextConstraint);
    }

}
