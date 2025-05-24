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

import java.util.List;

/**
 * Result returned for a {@link GetFacetStats} request.
 * <p>
 * Contains aggregated counts of documents per facet field value.
 */
@Value
public class GetFacetStatsResult implements RequestResult {

    /**
     * The identifier of the request that triggered this result.
     */
    long requestId;

    /**
     * The aggregated statistics for each facet field and its values.
     */
    List<FacetStats> stats;

    /**
     * Timestamp indicating when this result was generated (milliseconds since epoch).
     */
    long timestamp = System.currentTimeMillis();

    @Override
    public Metric toMetric() {
        return new Metric(stats.size(), timestamp);
    }

    /**
     * Lightweight summary of the facet statistics result, used for internal metric tracking.
     * <p>
     * This class is automatically published as a separate message to the {@code metrics} log by the
     * Flux client after completing the request.
     */
    @Value
    public static class Metric {
        /**
         * Number of facet fields included in the result.
         */
        int size;

        /**
         * Timestamp of result generation.
         */
        long timestamp;
    }
}