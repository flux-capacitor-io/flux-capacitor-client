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

package io.fluxcapacitor.common.api;

import lombok.Value;

import java.util.List;

/**
 * Represents a batch of results returned from the Flux platform in response to a {@link RequestBatch}.
 * <p>
 * Each {@link RequestResult} in this list corresponds to one of the requests in the original batch and is
 * matched using {@code requestId}. Note that result order is not guaranteed to match the order of the requests.
 * </p>
 *
 * <h2>Result Matching</h2>
 * The Flux client automatically routes each {@link RequestResult} back to its originating {@link Request}
 * using the unique {@code requestId}.
 *
 * <h2>Error Handling</h2>
 * If any request fails, its result will be an {@link ErrorResult}. The client will complete the
 * corresponding {@code CompletableFuture} exceptionally using a {@code ServiceException}.
 *
 * @see RequestBatch
 * @see RequestResult
 * @see ErrorResult
 */
@Value
public class ResultBatch implements JsonType {

    /**
     * The list of results corresponding to previously sent requests.
     * <p>
     * Result order is not guaranteed to match the original request order.
     * Use {@link RequestResult#getRequestId()} to match responses.
     */
    List<RequestResult> results;
}
