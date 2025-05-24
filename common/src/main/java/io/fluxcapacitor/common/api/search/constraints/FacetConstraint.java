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

import io.fluxcapacitor.common.api.HasId;
import io.fluxcapacitor.common.api.search.Constraint;
import io.fluxcapacitor.common.api.search.FacetEntry;
import io.fluxcapacitor.common.api.search.NoOpConstraint;
import io.fluxcapacitor.common.search.Document;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static io.fluxcapacitor.common.SearchUtils.normalizePath;
import static java.util.stream.Collectors.toList;

/**
 * A constraint that matches documents containing a specific {@link FacetEntry}.
 * <p>
 * Facets are indexed fields marked using {@code @Facet} annotations. Using this constraint allows for efficient lookups
 * because the facet matching is typically executed directly by the backing search store. This can make it significantly
 * faster than using a general {@link MatchConstraint} for the same purpose.
 *
 * <p>
 * Example usage to match a single facet:
 * <pre>{@code
 * Constraint facetMatch = FacetConstraint.matchFacet("country", "Netherlands");
 * }</pre>
 *
 * @see Constraint
 * @see io.fluxcapacitor.common.search.Facet
 * @see FacetEntry
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FacetConstraint implements Constraint {

    /**
     * Factory method to create a constraint that matches documents with the given facet name and value.
     * <p>
     * If the value is a {@link Collection}, multiple facets are matched using an {@link AnyConstraint} over the
     * individual values. If the value is a {@link HasId}, its identifier is used for matching.
     *
     * @param name  the facet field name (normalized internally)
     * @param value the expected facet value
     * @return a {@code FacetConstraint} or a composite constraint if {@code value} is a collection, or
     * {@link NoOpConstraint} if the name is null or the value is null/empty.
     */
    public static Constraint matchFacet(String name, Object value) {
        if (name == null) {
            return NoOpConstraint.instance;
        }
        var normalizedName = normalizePath(name);
        switch (value) {
            case Collection<?> objects -> {
                List<Constraint> constraints =
                        objects.stream().filter(Objects::nonNull)
                                .map(v -> new FacetConstraint(new FacetEntry(normalizedName, v.toString())))
                                .collect(toList());
                return switch (constraints.size()) {
                    case 0 -> NoOpConstraint.instance;
                    case 1 -> constraints.getFirst();
                    default -> AnyConstraint.any(constraints);
                };
            }
            case HasId id -> {
                return new FacetConstraint(new FacetEntry(normalizedName, id.getId()));
            }
            case null -> {
                return NoOpConstraint.instance;
            }
            default -> {
                return new FacetConstraint(new FacetEntry(normalizedName, value.toString()));
            }
        }
    }

    /**
     * Factory method to create a constraint that matches a single {@link FacetEntry}.
     *
     * @param facet the facet to match
     * @return a {@code FacetConstraint} for the provided facet or a {@link NoOpConstraint} if the facet is null
     */
    public static Constraint matchFacet(FacetEntry facet) {
        return facet == null ? NoOpConstraint.instance : new FacetConstraint(facet);
    }

    /**
     * The facet that must be present in the document for this constraint to match.
     */
    @NonNull FacetEntry facet;

    @Override
    public boolean matches(Document document) {
        return document.getFacets().contains(facet);
    }

    /**
     * This constraint does not apply to specific document paths, so this always returns {@code false}.
     */
    @Override
    public boolean hasPathConstraint() {
        return false;
    }
}
