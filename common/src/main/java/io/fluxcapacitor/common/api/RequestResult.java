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

/**
 * Marker interface for responses to {@link Request} objects (including commands and queries).
 * <p>
 * Implementations of this interface are returned by the Flux platform and matched to
 * their corresponding {@link Request} using the {@code requestId}.
 * </p>
 *
 * <p>
 * A typical {@code RequestResult} contains minimal payload and metadata for monitoring,
 * and may override {@link #toMetric()} to avoid logging full results.
 * </p>
 *
 * @see Request
 * @see ErrorResult
 */
public interface RequestResult extends JsonType {

    /**
     * The requestId of the original {@link Request} this result corresponds to.
     */
    long getRequestId();

    /**
     * The timestamp (in epoch milliseconds) when this result was generated.
     */
    long getTimestamp();
}
