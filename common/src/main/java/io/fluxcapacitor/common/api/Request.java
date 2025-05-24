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

import io.fluxcapacitor.common.api.search.GetDocument;
import io.fluxcapacitor.common.api.search.GetFacetStats;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for requests sent to the Flux platform.
 * <p>
 * Each {@code Request} is automatically assigned a unique {@link #requestId} to correlate it with its corresponding
 * {@link RequestResult}. This identifier is useful for tracing, debugging, and performance monitoring.
 * <p>
 * All requests implement {@link JsonType}, allowing them to optionally define a custom {@link JsonType#toMetric()}
 * representation for metrics logging.
 * <p>
 * Subclasses of this abstract class typically include payloads such as {@link SearchDocuments},
 * {@link GetDocument}, {@link GetFacetStats}, etc.
 *
 * @see JsonType
 * @see RequestResult
 */
@Getter
@EqualsAndHashCode
public abstract class Request implements JsonType {
    private static final AtomicLong nextRequestId = new AtomicLong();

    /**
     * A unique identifier automatically assigned to this request. Used to correlate with the response.
     */
    private final long requestId = nextRequestId.getAndIncrement();
}
