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

public class DefaultWebResponseMapper implements WebResponseMapper {
    @Override
    public WebResponse map(Object payload, Metadata metadata) {
        WebResponse.Builder builder = WebResponse.builder();
        if (payload instanceof Throwable) {
            if (payload instanceof ValidationException || payload instanceof DeserializationException) {
                builder.status(400);
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof UnauthorizedException || payload instanceof UnauthenticatedException) {
                builder.status(401);
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof FunctionalException) {
                builder.status(403);
                builder.payload(((Exception) payload).getMessage());
            } else if (payload instanceof TimeoutException
                    || payload instanceof io.fluxcapacitor.javaclient.publishing.TimeoutException) {
                builder.status(503);
                builder.payload("The request has timed out. Please try again later.");
            } else {
                builder.status(500);
                builder.payload("An unexpected error occurred.");
            }
        } else {
            builder.status(payload == null ? 204 : 200);
            builder.payload(payload);
        }
        return builder.build().addMetadata(metadata);
    }
}
