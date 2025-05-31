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
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthorizedException;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;

import java.util.concurrent.TimeoutException;

/**
 * Default implementation of {@link WebResponseMapper} that converts handler return values into {@link WebResponse} objects.
 * <p>
 * This mapper is used internally to standardize responses from {@code @HandleWeb} methods or forwarded HTTP requests.
 * It maps return values based on their type, applying common HTTP status codes and payload formatting conventions.
 *
 * <h2>Mapping Rules</h2>
 * The logic follows these conventions:
 * <ul>
 *   <li>If the response is already a {@link WebResponse}, it is returned unchanged</li>
 *   <li>If the response is a known {@link Throwable}, a corresponding HTTP error code and message are assigned:
 *     <ul>
 *       <li>{@link ValidationException} or {@link DeserializationException} → 400 Bad Request</li>
 *       <li>{@link UnauthorizedException} or {@link UnauthenticatedException} → 401 Unauthorized</li>
 *       <li>other {@link FunctionalException}s → 403 Forbidden</li>
 *       <li>{@link java.util.concurrent.TimeoutException} or Flux’s own {@code TimeoutException} → 503 Service Unavailable</li>
 *       <li>Any other {@code Throwable} → 500 Internal Server Error</li>
 *     </ul>
 *   </li>
 *   <li>If the response is {@code null}, the status is set to 204 No Content</li>
 *   <li>Otherwise, the response is treated as a normal payload with status 200 OK</li>
 * </ul>
 *
 * <p>In all cases, the provided {@link Metadata} is added to the resulting response.
 *
 * @see WebResponseMapper
 * @see WebResponse
 * @see HandleWeb
 */
public class DefaultWebResponseMapper implements WebResponseMapper {

    /**
     * Maps a raw handler return value and associated metadata into a {@link WebResponse}.
     *
     * @param response the return value (may be a {@link WebResponse}, a regular object, or a {@link Throwable})
     * @param metadata metadata to attach to the response
     * @return a mapped {@link WebResponse} object
     */
    @Override
    public WebResponse map(Object response, Metadata metadata) {
        if (response instanceof WebResponse r) {
            return r;
        }
        WebResponse.Builder builder = WebResponse.builder();
        if (response instanceof Throwable) {
            if (response instanceof ValidationException || response instanceof DeserializationException) {
                builder.status(400).payload(((Exception) response).getMessage());
            } else if (response instanceof UnauthorizedException || response instanceof UnauthenticatedException) {
                builder.status(401).payload(((Exception) response).getMessage());
            } else if (response instanceof FunctionalException) {
                builder.status(403).payload(((Exception) response).getMessage());
            } else if (response instanceof TimeoutException
                       || response instanceof io.fluxcapacitor.javaclient.publishing.TimeoutException) {
                builder.status(503).payload("The request has timed out. Please try again later.");
            } else {
                builder.status(500).payload("An unexpected error occurred.");
            }
        } else {
            builder.status(response == null ? 204 : 200).payload(response);
        }
        return builder.build().addMetadata(metadata);
    }
}
