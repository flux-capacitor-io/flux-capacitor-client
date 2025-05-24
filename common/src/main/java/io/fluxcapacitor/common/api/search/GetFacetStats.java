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

import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Request to retrieve aggregated counts for all {@link FacetEntry facet} fields across documents matching the given
 * {@link SearchQuery}.
 * <p>
 * This is useful for generating filter menus, dashboards, or visualizations that break down the search results by key
 * categorical dimensions (e.g., status, region, user role).
 * <p>
 * Only fields marked with {@code @Facet} in the domain model will be considered.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class GetFacetStats extends Request {

    /**
     * The search query used to filter the documents on which facet aggregation will be performed.
     */
    SearchQuery query;
}
