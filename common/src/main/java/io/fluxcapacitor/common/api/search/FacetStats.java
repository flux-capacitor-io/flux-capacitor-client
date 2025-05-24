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

import io.fluxcapacitor.common.search.Facet;
import lombok.Value;

/**
 * Represents the count of documents matching a specific facet value.
 * <p>
 * These statistics are typically returned by a {@link io.fluxcapacitor.common.api.search.GetFacetStatsResult}
 * in response to a {@link io.fluxcapacitor.common.api.search.GetFacetStats} request.
 * <p>
 * Facet statistics are computed by the backing document store for fields annotated with {@link Facet},
 * making them fast to compute even on large datasets.
 *
 * @see io.fluxcapacitor.common.api.search.GetFacetStats
 * @see io.fluxcapacitor.common.api.search.GetFacetStatsResult
 * @see Facet
 */
@Value
public class FacetStats {

    /**
     * The name of the facet field, typically matching a path in the document that was annotated with {@link Facet}.
     */
    String name;

    /**
     * The specific value of the facet for which the count applies.
     */
    String value;

    /**
     * The number of documents that have the specified facet {@link #value} at the given {@link #name} path.
     */
    int count;
}
