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
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * A composite constraint that requires all inner constraints to match.
 * <p>
 * This is equivalent to a logical <strong>AND</strong> operation over the contained constraints. The document must
 * satisfy every inner constraint in the list for the {@code AllConstraint} to match.
 *
 * <p>
 * If the constraint list is empty, this becomes a no-op constraint that matches all documents. If only one constraint
 * is provided, it is returned directly to avoid unnecessary nesting.
 *
 * @see AnyConstraint for logical OR
 * @see NotConstraint for logical negation
 * @see Constraint
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AllConstraint implements Constraint {

    /**
     * Creates an {@code AllConstraint} from the given array of constraints.
     * <p>
     * If the array is empty, a {@link NoOpConstraint} is returned. If only one constraint is provided, that constraint
     * is returned directly.
     *
     * @param constraints one or more constraints to combine
     * @return a constraint that requires all inputs to match
     */
    public static Constraint all(Constraint... constraints) {
        return all(Arrays.asList(constraints));
    }

    /**
     * Creates an {@code AllConstraint} from the given collection of constraints.
     * <p>
     * If the collection is empty, a {@link NoOpConstraint} is returned. If it contains only one constraint, that
     * constraint is returned directly.
     *
     * @param constraints a collection of constraints to combine
     * @return a constraint that requires all inputs to match
     */
    public static Constraint all(Collection<Constraint> constraints) {
        var list = constraints.stream().distinct().collect(toList());
        return switch (list.size()) {
            case 0 -> NoOpConstraint.instance;
            case 1 -> list.getFirst();
            default -> new AllConstraint(list);
        };
    }

    /**
     * The list of constraints being combined using a logical AND.
     */
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

}
