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

import lombok.Value;

import java.time.Instant;
import java.util.List;

/**
 * Represents a histogram over a time range, typically returned by
 * {@link io.fluxcapacitor.common.api.search.GetSearchHistogramResult}.
 * <p>
 * This class captures how many documents fall into equally sized time buckets between {@code since} and {@code before}.
 * It is often used for visualizing activity over time (e.g., in a timeline or bar chart).
 * <p>
 * The number of buckets (i.e. the size of {@code counts}) is determined by the {@code resolution}
 * parameter in the original {@link io.fluxcapacitor.common.api.search.GetSearchHistogram} request.
 *
 * @see io.fluxcapacitor.common.api.search.GetSearchHistogram
 * @see io.fluxcapacitor.common.api.search.GetSearchHistogramResult
 */
@Value
public class SearchHistogram {
    /**
     * The start of the histogram range (inclusive).
     */
    Instant since;

    /**
     * The end of the histogram range (exclusive).
     */
    Instant before;

    /**
     * The list of counts per time bucket. Always has {@code resolution} entries.
     */
    List<Long> counts;
}
