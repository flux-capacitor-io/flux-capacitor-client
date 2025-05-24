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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Comparator;

/**
 * Represents a single facet field-value pair within a document.
 * <p>
 * Facet entries are typically included in {@link io.fluxcapacitor.common.api.search.SerializedDocument} instances
 * to make the document searchable and filterable by discrete values. These entries are also used when computing
 * facet statistics and filtering using {@link io.fluxcapacitor.common.api.search.constraints.FacetConstraint}.
 * <p>
 * The {@link #compareTo(FacetEntry)} implementation ensures consistent ordering of facet entries first by name,
 * then by value.
 *
 * @see io.fluxcapacitor.common.api.search.SerializedDocument
 * @see io.fluxcapacitor.common.api.search.FacetStats
 * @see io.fluxcapacitor.common.api.search.constraints.FacetConstraint
 * @see io.fluxcapacitor.common.search.Facet
 */
@Value
@Builder(toBuilder = true)
@AllArgsConstructor
public class FacetEntry implements Comparable<FacetEntry> {

    private static final Comparator<FacetEntry> comparator =
            Comparator.comparing(FacetEntry::getName).thenComparing(FacetEntry::getValue);

    /**
     * The name of the facet field (i.e., the document path it represents).
     */
    @NonNull String name;

    /**
     * The value associated with the facet field for this document.
     */
    @NonNull String value;

    /**
     * Sorts facet entries by name, then by value.
     *
     * @param o the other facet entry to compare against
     * @return the result of comparing name first, then value
     */
    @Override
    public int compareTo(@NonNull FacetEntry o) {
        return comparator.compare(this, o);
    }
}
