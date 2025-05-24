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
 * Response to a {@link io.fluxcapacitor.common.api.search.GetDocumentStats} request.
 * <p>
 * Contains aggregated statistics for documents that matched a {@link SearchQuery}, grouped and computed
 * in-memory. Statistics include common numeric metrics like count, min, max, sum, and average, organized
 * per field and optional grouping.
 * <p>
 * For performance-sensitive use cases or large data sets, prefer {@link io.fluxcapacitor.common.api.search.GetFacetStats},
 * which is computed by the backing store and generally faster.
 *
 * @see io.fluxcapacitor.common.api.search.GetDocumentStats
 * @see DocumentStats
 */
@Value
public class GetDocumentStatsResult implements RequestResult {

    /**
     * The unique identifier of the originating request.
     */
    long requestId;

    /**
     * Aggregated statistics for matched document groups.
     */
    List<DocumentStats> documentStats;

    /**
     * Timestamp when the statistics were computed.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Returns a lightweight summary of this result for logging or metric tracking.
     */
    @Override
    public Metric toMetric() {
        return new Metric(documentStats.size(), timestamp);
    }

    /**
     * Compact summary of the result, used for internal monitoring purposes.
     */
    @Value
    public static class Metric {
        int size;
        long timestamp;
    }
}