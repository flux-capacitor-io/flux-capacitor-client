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

package io.fluxcapacitor.javaclient.common.exception;

import io.fluxcapacitor.common.api.ErrorResult;

import java.util.concurrent.CompletableFuture;

/**
 * Exception thrown when a {@link io.fluxcapacitor.common.api.Request} fails and an {@link ErrorResult} is returned by
 * the Flux platform.
 * <p>
 * This exception is used to complete the {@link CompletableFuture} of a failed request with the message from the
 * associated {@code ErrorResult}.
 * </p>
 */
public class ServiceException extends RuntimeException {

    /**
     * Constructs a new exception with the specified error message.
     *
     * @param message the message describing the failure
     */
    public ServiceException(String message) {
        super(message);
    }
}
