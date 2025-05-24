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

import java.util.List;

/**
 * Request to compute statistics on documents that match a given {@link SearchQuery}.
 * <p>
 * This operation supports flexible aggregations on arbitrary document fields, including numeric ranges, value
 * frequencies, and groupings. It can be used, for example, to calculate total counts, averages, or distributions
 * grouped by specified field(s).
 * <p>
 * <strong>Performance note:</strong> This request is typically resolved <em>in-memory</em> by the Flux platform.
 * As a result, it may be expensive to execute for large result sets or unindexed fields.
 * <p>
 * If you only need count statistics for fields explicitly marked with {@link io.fluxcapacitor.common.search.Facet},
 * consider using {@link GetFacetStats} instead. That variant leverages the backing document store and is significantly
 * more efficient.
 * <p>
 * Example use cases:
 * <ul>
 *     <li>Calculate how many documents exist per status code or country.</li>
 *     <li>Group average document sizes by collection name.</li>
 *     <li>Compute time-based activity levels based on a {@code createdAt} timestamp.</li>
 * </ul>
 *
 * @see GetFacetStats
 * @see SearchQuery
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class GetDocumentStats extends Request {
    /**
     * Query that filters which documents are included in the statistics.
     */
    SearchQuery query;

    /**
     * List of fields to include in the statistics output (e.g., {@code status}, {@code region}, {@code amount}).
     */
    List<String> fields;

    /**
     * Optional list of field names to group results by (e.g., {@code type}, {@code customerId}).
     */
    List<String> groupBy;
}
