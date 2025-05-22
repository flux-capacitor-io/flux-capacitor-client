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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.ResponseMapper;

/**
 * Specialization of {@link ResponseMapper} for mapping responses to {@link WebResponse} messages.
 * <p>
 * This interface provides a default implementation that handles {@link Message} instances directly,
 * extracting their payload and metadata before invoking the two-argument {@link #map(Object, Metadata)} method.
 * </p>
 *
 * <p>
 * Used for mapping the return values of web request handlers into {@code WebResponse} objects to be
 * published to the {@code WebResponse} log.
 * </p>
 *
 * @see WebResponse
 * @see HandleWeb
 */
public interface WebResponseMapper extends ResponseMapper {

    /**
     * Maps a generic response object to a {@link WebResponse}.
     * If the input is already a {@link Message}, its payload and metadata are extracted and reused.
     *
     * @param response the response object
     * @return a {@link WebResponse} representing the mapped response
     */
    @Override
    default WebResponse map(Object response) {
        return response instanceof Message m ? map(m.getPayload(), m.getMetadata()) : map(response, Metadata.empty());
    }

    /**
     * Maps a response and optional metadata into a {@link WebResponse}.
     *
     * @param response the raw response object
     * @param metadata associated metadata to include in the response
     * @return a {@link WebResponse} with payload and metadata
     */
    @Override
    WebResponse map(Object response, Metadata metadata);
}
