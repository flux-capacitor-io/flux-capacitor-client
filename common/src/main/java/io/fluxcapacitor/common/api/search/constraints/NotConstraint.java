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
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * A constraint that negates the result of another constraint.
 * <p>
 * This is equivalent to a logical <strong>NOT</strong>. A document matches this constraint if it does <em>not</em>
 * match the provided inner constraint.
 *
 * <p>
 * Example: to find all documents that do <em>not</em> have a particular value:
 * <pre>{@code
 * Constraint notAdmin = NotConstraint.not(MatchConstraint.match("admin", "userRole"));
 * }</pre>
 *
 * @see AllConstraint for logical AND
 * @see AnyConstraint for logical OR
 * @see Constraint
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotConstraint implements Constraint {

    /**
     * Factory method to create a {@code NotConstraint}.
     *
     * @param constraint the constraint to negate
     * @return a constraint that matches if the input constraint does not match
     */
    public static NotConstraint not(Constraint constraint) {
        return new NotConstraint(constraint);
    }

    /**
     * The inner constraint that is being negated.
     */
    @NonNull Constraint not;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true) @Accessors(fluent = true)
    Constraint decompose = new NotConstraint(getNot().decompose());

    @Override
    public boolean matches(Document document) {
        return !not.matches(document);
    }

    @Override
    public boolean hasPathConstraint() {
        return not.hasPathConstraint();
    }

}
