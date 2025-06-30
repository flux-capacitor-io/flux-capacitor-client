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
 * Result returned in response to a {@link io.fluxcapacitor.common.api.search.GetDocument} request.
 * <p>
 * This result includes a single {@link SerializedDocument} identified by its ID and collection,
 * or {@code null} if the document could not be found.
 *
 * @see io.fluxcapacitor.common.api.search.GetDocument
 * @see SerializedDocument
 */
@Value
public class GetDocumentResult implements RequestResult {

    /**
     * The ID of the request that triggered this result. Used to correlate the response with the original request.
     */
    long requestId;

    /**
     * The document that was retrieved, or {@code null} if no matching document was found.
     */
    SerializedDocument document;

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
        return new Metric(timestamp, document != null, document != null ? document.bytes() : 0);
    }

    /**
     * Lightweight structure for representing {@link GetDocumentResult} in Flux metrics logs.
     */
    @Value
    public static class Metric {

        /**
         * The time (in milliseconds since epoch) when the response was generated.
         */
        long timestamp;

        /**
         * Whether a document was found in the search store.
         */
        boolean found;

        /**
         * The size of the document in bytes.
         */
        int bytes;
    }
}