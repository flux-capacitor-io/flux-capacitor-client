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

import java.util.Collection;

/**
 * Result returned in response to a {@link GetDocuments} request.
 * <p>
 * This result includes a collection of {@link SerializedDocument documents} identified by their ID and collection,
 * or {@code null} if the document could not be found.
 *
 * @see GetDocument
 * @see SerializedDocument
 */
@Value
public class GetDocumentsResult implements RequestResult {

    /**
     * The ID of the request that triggered this result. Used to correlate the response with the original request.
     */
    long requestId;

    /**
     * The documents that were retrieved. Maybe fewer than the ones requested if those documents could not be found.
     */
    Collection<SerializedDocument> documents;

    /**
     * The system time (in milliseconds since epoch) at which this result was generated.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Converts this result to a compact representation for metrics logging.
     *
     * @return a {@link Metric} containing only the response timestamp.
     */
    @Override
    public Metric toMetric() {
        return new Metric(timestamp, documents.stream().map(SerializedDocument::getId).toList(), documents.stream().mapToLong(SerializedDocument::bytes).sum());
    }

    /**
     * Lightweight structure for representing {@link GetDocumentsResult} in Flux metrics logs.
     */
    @Value
    public static class Metric {

        /**
         * The time (in milliseconds since epoch) when the response was generated.
         */
        long timestamp;

        /**
         * The ids of retrieved documents.
         */
        Collection<String> idsFound;

        /**
         * The size of all the retrieved documents in bytes.
         */
        long bytes;
    }
}