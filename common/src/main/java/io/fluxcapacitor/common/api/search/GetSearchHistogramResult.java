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

import io.fluxcapacitor.common.api.RequestResult;
import lombok.Value;

/**
 * Result of a {@link io.fluxcapacitor.common.api.search.GetSearchHistogram} request.
 * <p>
 * This result contains a {@link SearchHistogram} that provides time-distribution statistics
 * over matching documents, typically for visualization purposes like timeline charts or activity heatmaps.
 *
 * @see io.fluxcapacitor.common.api.search.GetSearchHistogram
 * @see SearchHistogram
 */
@Value
public class GetSearchHistogramResult implements RequestResult {

    /**
     * The unique identifier of the original histogram request.
     */
    long requestId;

    /**
     * The computed histogram based on the matched document timestamps.
     */
    SearchHistogram histogram;

    /**
     * The timestamp (epoch millis) when this result was created, used for logging and metrics.
     */
    long timestamp = System.currentTimeMillis();
}