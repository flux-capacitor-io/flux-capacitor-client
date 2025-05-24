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
 * The result of a {@link io.fluxcapacitor.common.api.search.SearchDocuments} request.
 * <p>
 * This result contains the list of {@link SerializedDocument} matches that satisfied the search query.
 *
 * @see io.fluxcapacitor.common.api.search.SearchDocuments
 * @see SerializedDocument
 */
@Value
public class SearchDocumentsResult implements RequestResult {

    /**
     * The unique identifier of the originating search request.
     */
    long requestId;

    /**
     * The list of documents that matched the search query.
     */
    List<SerializedDocument> matches;

    /**
     * The timestamp (epoch millis) indicating when this result was created.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Converts this result into a serializable metrics object that logs match count and timing,
     * instead of full search result payloads.
     */
    @Override
    public Metric toMetric() {
        return new Metric(matches.size(), timestamp);
    }

    /**
     * Returns the number of matched documents.
     */
    public int size() {
        return matches.size();
    }

    /**
     * Returns the last document in the result list, or {@code null} if no matches were found.
     * <p>
     * This is typically used for pagination (e.g. passing as {@code lastHit} in a follow-up search).
     */
    public SerializedDocument lastMatch() {
        return matches.isEmpty() ? null : matches.getLast();
    }

    /**
     * Lightweight metric representation used for logging search result metadata.
     */
    @Value
    public static class Metric {
        int size;
        long timestamp;
    }
}