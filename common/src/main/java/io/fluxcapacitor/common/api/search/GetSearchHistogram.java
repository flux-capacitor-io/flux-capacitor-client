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
 * Request to compute a time-based histogram over documents that match a given query.
 * <p>
 * This is typically used to visualize activity or value distributions over time. The histogram
 * aggregates document timestamps into evenly spaced buckets, determined by {@code resolution}.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class GetSearchHistogram extends Request {

    /**
     * The query that determines which documents are included in the histogram.
     */
    SearchQuery query;

    /**
     * The number of buckets (time intervals) to divide the histogram into.
     */
    int resolution;

    /**
     * The maximum number of documents to consider for performance reasons.
     */
    int maxSize;
}
