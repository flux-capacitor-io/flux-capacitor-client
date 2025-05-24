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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.search.constraints.AllConstraint;
import io.fluxcapacitor.common.api.search.constraints.AnyConstraint;
import io.fluxcapacitor.common.api.search.constraints.BetweenConstraint;
import io.fluxcapacitor.common.api.search.constraints.ContainsConstraint;
import io.fluxcapacitor.common.api.search.constraints.ExistsConstraint;
import io.fluxcapacitor.common.api.search.constraints.FacetConstraint;
import io.fluxcapacitor.common.api.search.constraints.LookAheadConstraint;
import io.fluxcapacitor.common.api.search.constraints.MatchConstraint;
import io.fluxcapacitor.common.api.search.constraints.NotConstraint;
import io.fluxcapacitor.common.api.search.constraints.PathConstraint;
import io.fluxcapacitor.common.api.search.constraints.QueryConstraint;
import io.fluxcapacitor.common.search.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Base interface for defining filter conditions (constraints) used in document search queries.
 * <p>
 * Constraints determine whether a given {@link io.fluxcapacitor.common.search.Document} matches
 * specific criteria. Implementations of this interface can target paths, values, metadata,
 * or structural characteristics of the document.
 *
 * <h2>Serialization</h2>
 * This interface uses Jackson’s {@code @JsonTypeInfo} with deduction, enabling automatic deserialization
 * of concrete types based on structure. If no recognizable subtype is matched, {@link NoOpConstraint} is used.
 * <p>
 * Subtypes include:
 * <ul>
 *     <li>{@link AllConstraint} – Logical AND of multiple constraints</li>
 *     <li>{@link AnyConstraint} – Logical OR of multiple constraints</li>
 *     <li>{@link MatchConstraint} – Matches exact phrases or terms</li>
 *     <li>{@link ContainsConstraint} – Substring matching on field values</li>
 *     <li>{@link BetweenConstraint} – Numeric or date range filtering</li>
 *     <li>{@link ExistsConstraint} – Matches documents with a non-null value at a path</li>
 *     <li>{@link QueryConstraint} – Full-text search syntax supporting wildcards and advanced logic</li>
 *     <li>{@link NotConstraint} – Negates a constraint</li>
 *     <li>{@link LookAheadConstraint} – Predictive phrase/word matching based on user typing, commonly used for user-facing document search</li>
 *     <li>{@link FacetConstraint} – Filters based on field facets (used for drilldown/aggregation)</li>
 * </ul>
 *
 * <h2>Composability</h2>
 * Constraints can be composed into larger expressions using {@link #and(Constraint)} and {@link #or(Constraint)}:
 * <pre>{@code
 * Constraint c = MatchConstraint.match("value", "path1")
 *     .and(ExistsConstraint.exists("path2"))
 *     .or(ContainsConstraint.contains("fragment", "description"));
 * }</pre>
 *
 * @see io.fluxcapacitor.common.search.Document
 * @see PathConstraint
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, defaultImpl = NoOpConstraint.class)
@JsonSubTypes({@Type(AllConstraint.class), @Type(AnyConstraint.class), @Type(ContainsConstraint.class),
        @Type(BetweenConstraint.class), @Type(ExistsConstraint.class), @Type(QueryConstraint.class),
        @Type(MatchConstraint.class), @Type(NotConstraint.class), @Type(LookAheadConstraint.class),
        @Type(FacetConstraint.class)})
public interface Constraint {

    /**
     * Evaluates whether this constraint applies to the given document.
     *
     * @param document the document to test
     * @return {@code true} if the constraint matches the document
     */
    boolean matches(Document document);

    /**
     * Indicates whether this constraint targets specific paths in the document.
     *
     * @return {@code true} if path-based filtering is involved
     */
    boolean hasPathConstraint();

    /**
     * Returns a version of this constraint with all composite constraints decomposed into their atomic elements.
     * <p>
     * Useful for precomputing optimized structures (e.g. via {@link AllConstraint} or {@link AnyConstraint}).
     *
     * @return a simplified version of the constraint
     */
    default Constraint decompose() {
        return this;
    }

    /**
     * Combines this constraint with another using logical AND.
     *
     * @param other the other constraint
     * @return a new {@link AllConstraint} combining both
     */
    default Constraint and(Constraint other) {
        List<Constraint> constraints = new ArrayList<>();
        if (this instanceof AllConstraint) {
            constraints.addAll(((AllConstraint) this).getAll());
        } else {
            constraints.add(this);
        }
        if (other instanceof AllConstraint) {
            constraints.addAll(((AllConstraint) other).getAll());
        } else {
            constraints.add(other);
        }
        return AllConstraint.all(constraints);
    }

    /**
     * Combines this constraint with another using logical OR.
     *
     * @param other the other constraint
     * @return a new {@link AnyConstraint} combining both
     */
    default Constraint or(Constraint other) {
        List<Constraint> constraints = new ArrayList<>();
        if (this instanceof AnyConstraint) {
            constraints.addAll(((AnyConstraint) this).getAny());
        } else {
            constraints.add(this);
        }
        if (other instanceof AnyConstraint) {
            constraints.addAll(((AnyConstraint) other).getAny());
        } else {
            constraints.add(other);
        }
        return AnyConstraint.any(constraints);
    }
}
