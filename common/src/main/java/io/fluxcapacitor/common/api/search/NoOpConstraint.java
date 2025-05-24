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

package io.fluxcapacitor.common.api.search;

import io.fluxcapacitor.common.api.search.constraints.FacetConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * A {@link Constraint} implementation that matches all documents and imposes no filtering conditions.
 * <p>
 * This is typically used as a placeholder or default when no real constraint is needed. It always returns {@code true}
 * for {@link #matches(Document)} and is considered to have no path constraints.
 * <p>
 * This constraint is used internally by various factory methods (such as those in {@link MatchConstraint},
 * {@link FacetConstraint}, etc.) when an input is null, empty, or otherwise does not yield a meaningful constraint.
 *
 * <p>For example, {@code MatchConstraint.match(null)} returns a {@code NoOpConstraint}.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NoOpConstraint implements Constraint {

    /**
     * Singleton instance of the no-op constraint.
     */
    public static final NoOpConstraint instance = new NoOpConstraint();

    /**
     * Always returns {@code true}, indicating that any document matches.
     */
    @Override
    public boolean matches(Document document) {
        return true;
    }

    /**
     * Always returns {@code false}, as this constraint is not tied to any document path.
     */
    @Override
    public boolean hasPathConstraint() {
        return false;
    }

}
