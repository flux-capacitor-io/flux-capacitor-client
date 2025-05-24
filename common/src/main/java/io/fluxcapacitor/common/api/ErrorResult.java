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

import java.util.concurrent.CompletableFuture;

/**
 * A generic error response returned when a request could not be completed successfully.
 * <p>
 * Contains a textual error message explaining the failure. This class is often returned in place of the expected
 * {@link RequestResult} if an error occurs in the Flux platform or client.
 * </p>
 *
 * <h2>Exception Behavior</h2>
 * If an {@code ErrorResult} is received for a {@link Request}, the associated {@link CompletableFuture} will be
 * completed exceptionally with a {@code ServiceException} that wraps the error message. This allows applications to
 * handle errors via standard {@code future.exceptionally(...)} mechanisms.
 *
 * <pre>{@code
 * keyValueClient.send(new GetValue("missingKey"))
 *            .exceptionally(error -> {
 *                if (error instanceof ServiceException) {
 *                    log.warn("Failed: " + error.getMessage());
 *                }
 *                return null;
 *            });
 * }</pre>
 *
 * @see RequestResult
 */
@Value
public class ErrorResult implements RequestResult {

    /**
     * ID correlating this result with its originating request.
     */
    long requestId;

    /**
     * Time at which the error occurred.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * The error message returned from the Flux platform or client logic.
     */
    String message;
}