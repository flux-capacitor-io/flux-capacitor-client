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
 * A composite constraint that requires at least one inner constraint to match.
 * <p>
 * This is equivalent to a logical <strong>OR</strong> operation over the contained constraints. The document matches
 * if any of the provided constraints evaluate to true.
 *
 * <p>
 * If the constraint list is empty, a {@link NoOpConstraint} is returned (which always matches). If only one constraint
 * is provided, it is returned directly without wrapping in an {@code AnyConstraint}.
 *
 * @see AllConstraint for logical AND
 * @see NotConstraint for logical negation
 * @see Constraint
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AnyConstraint implements Constraint {

    /**
     * Creates an {@code AnyConstraint} from the given array of constraints.
     * <p>
     * If the array is empty, a {@link NoOpConstraint} is returned. If only one constraint is provided, that constraint
     * is returned directly.
     *
     * @param constraints one or more constraints to combine
     * @return a constraint that matches if any input matches
     */
    public static Constraint any(Constraint... constraints) {
        return any(Arrays.asList(constraints));
    }

    /**
     * Creates an {@code AnyConstraint} from the given collection of constraints.
     * <p>
     * If the collection is empty, a {@link NoOpConstraint} is returned. If it contains only one constraint,
     * that constraint is returned directly.
     *
     * @param constraints a collection of constraints to combine
     * @return a constraint that matches if any input matches
     */
    public static Constraint any(Collection<Constraint> constraints) {
        var list = constraints.stream().distinct().collect(toList());
        return switch (list.size()) {
            case 0 -> NoOpConstraint.instance;
            case 1 -> list.getFirst();
            default -> new AnyConstraint(list);
        };
    }

    /**
     * The list of constraints being combined using a logical OR.
     */
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
