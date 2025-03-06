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

@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FacetConstraint implements Constraint {
    public static Constraint matchFacet(String name, Object value) {
        if (name == null) {
            return NoOpConstraint.instance;
        }
        var normalizedName = normalizePath(name);
        if (value instanceof Collection<?>) {
            List<Constraint> constraints =
                    ((Collection<?>) value).stream().filter(Objects::nonNull)
                            .map(v -> new FacetConstraint(new FacetEntry(normalizedName, v.toString())))
                            .collect(toList());
            return switch (constraints.size()) {
                case 0 -> NoOpConstraint.instance;
                case 1 -> constraints.getFirst();
                default -> AnyConstraint.any(constraints);
            };
        } else {
            return value == null
                    ? NoOpConstraint.instance : new FacetConstraint(new FacetEntry(normalizedName, value.toString()));
        }
    }

    public static Constraint matchFacet(FacetEntry facet) {
        return facet == null ? NoOpConstraint.instance : new FacetConstraint(facet);
    }

    @NonNull FacetEntry facet;

    @Override
    public boolean matches(Document document) {
        return document.getFacets().contains(facet);
    }

    @Override
    public boolean hasPathConstraint() {
        return false;
    }
}
