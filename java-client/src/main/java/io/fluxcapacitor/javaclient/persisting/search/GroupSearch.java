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

package io.fluxcapacitor.javaclient.persisting.search;

import io.fluxcapacitor.common.api.search.DocumentStats.FieldStats;
import io.fluxcapacitor.common.api.search.Group;

import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * Represents a grouped search query in the Flux Capacitor search framework.
 * <p>
 * This interface is typically obtained from a {@link Search} instance via {@link Search#groupBy(String...)}.
 * It allows aggregations to be performed within groups defined by one or more field values.
 * Each unique combination of the specified group fields defines a {@link Group}, and aggregate results
 * are computed separately for each group.
 *
 * <p>For example, if grouping by {@code "country"} and aggregating {@code "revenue"},
 * the result will contain one entry per country with the aggregated revenue statistics per group.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Map<Group, Map<String, FieldStats>> revenueByCountry = search.groupBy("country").aggregate("revenue");
 * }</pre>
 *
 * @see Search#groupBy(String...)
 * @see Group
 * @see FieldStats
 */
public interface GroupSearch {

    /**
     * Performs aggregation over the given fields for each group.
     * <p>
     * Each {@link Group} represents a unique combination of field values for the group-by fields.
     * The returned map provides aggregate statistics (such as count, min, max, average, etc.)
     * for each of the specified fields within each group.
     *
     * @param fields The fields to aggregate within each group.
     * @return A map from group identifiers to aggregated statistics per field.
     */
    Map<Group, Map<String, FieldStats>> aggregate(String... fields);

    /**
     * Counts the number of documents in each group.
     * <p>
     * This is a shorthand for aggregating with default count statistics and extracting the count
     * for the first aggregated field in each group.
     *
     * @return A map from group identifiers to document counts.
     */
    default Map<Group, Long> count() {
        return aggregate().entrySet().stream().collect(toMap(Map.Entry::getKey, e ->
                e.getValue().values().stream().findFirst().map(FieldStats::getCount).orElse(0L)));
    }
}
