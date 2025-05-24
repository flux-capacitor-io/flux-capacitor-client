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
 * Represents a batch of requests to be sent to the Flux platform in a single network round-trip.
 * <p>
 * Used internally by the Flux client to optimize performance by batching multiple {@link Request} instances into a
 * single payload. This reduces round-trips and improves throughput when many requests are sent in quick succession.
 * </p>
 *
 * <h2>Usage</h2>
 * While most users won't construct {@code RequestBatch} manually, it's useful to know that multiple requests (e.g.
 * queries or commands) may be transparently wrapped and sent together.
 *
 * @param <T> the type of individual request, typically a subtype of {@link JsonType} (e.g., {@link Request})
 * @see ResultBatch
 */
@Value
public class RequestBatch<T extends JsonType> implements JsonType {

    /**
     * The list of requests to be sent together in this batch.
     */
    List<T> requests;
}
