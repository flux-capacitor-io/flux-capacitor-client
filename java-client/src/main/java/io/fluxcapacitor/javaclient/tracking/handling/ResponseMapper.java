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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

/**
 * A strategy interface for converting arbitrary response objects into {@link Message} instances.
 * <p>
 * This abstraction allows handler return values (or other types of responses) to be wrapped in a consistent message
 * format, optionally including {@link Metadata}.
 * </p>
 *
 * <p>
 * Implementations may handle different return types (e.g., plain objects, enriched result objects, or already wrapped
 * {@code Message} instances) depending on the needs of the message pipeline.
 * </p>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Transforming handler return values into {@link Message} envelopes for result publication</li>
 *   <li>Injecting custom metadata or headers into result messages</li>
 *   <li>Supporting custom serialization formats or protocols</li>
 * </ul>
 *
 * @see Message
 * @see Metadata
 */
public interface ResponseMapper {

    /**
     * Maps the given response object to a {@link Message}. The response may be a plain object or a {@link Message}.
     *
     * @param response the response object to be mapped
     * @return a {@link Message} representing the response
     */
    Message map(Object response);

    /**
     * Maps the given response object and metadata to a {@link Message}.
     *
     * @param response the response object to be transformed
     * @param metadata optional metadata to include in the message
     * @return a {@link Message} representing the response and metadata
     */
    Message map(Object response, Metadata metadata);
}
