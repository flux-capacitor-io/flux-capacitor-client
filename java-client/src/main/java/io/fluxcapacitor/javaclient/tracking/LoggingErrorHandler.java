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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.common.exception.FunctionalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * The default {@link ErrorHandler} implementation used by Flux Capacitor consumers.
 * <p>
 * This handler logs errors that occur during message tracking and processing, then allows tracking to continue. It is
 * intended for general-purpose use where robustness is more important than fail-fast behavior, but silent failure is
 * undesirable.
 *
 * <p><strong>Logging Behavior:</strong>
 * <ul>
 *     <li>Technical exceptions (i.e., not {@link FunctionalException}) are always logged at {@code ERROR} level.</li>
 *     <li>{@link FunctionalException}s are logged at {@code WARN} level if {@code logFunctionalErrors} is {@code true}.</li>
 *     <li>The error message and full exception are included in logs to aid observability.</li>
 * </ul>
 *
 * <p><strong>Control Flow:</strong>
 * <ul>
 *     <li>Tracking always continues; no exception is thrown.</li>
 *     <li>The original error is returned, and may be published as a {@code Result} message.</li>
 *     <li>The {@code retryFunction} is not executed in this implementation.</li>
 * </ul>
 *
 * <p><strong>Usage:</strong> This handler is used by default if no custom {@code errorHandler} is specified in the
 * {@link Consumer} annotation or {@link ConsumerConfiguration}.
 *
 * <pre>{@code
 * @Consumer(name = "default")
 * public class MyHandler {
 *     @HandleEvent
 *     void on(UserRegistered event) {
 *         // Recoverable errors will be logged and processing continues
 *     }
 * }
 * }</pre>
 *
 * @see ErrorHandler
 * @see ThrowingErrorHandler
 * @see SilentErrorHandler
 */
@Slf4j
@AllArgsConstructor
public class LoggingErrorHandler implements ErrorHandler {

    /**
     * Whether to log {@link FunctionalException}s at {@code WARN} level. Technical errors are always logged at
     * {@code ERROR}.
     */
    private final boolean logFunctionalErrors;

    /**
     * Constructs a {@code LoggingErrorHandler} that logs both technical and functional errors.
     */
    public LoggingErrorHandler() {
        this(true);
    }

    /**
     * Logs the given error and allows tracking to continue.
     *
     * @param error         the {@link Throwable} encountered during message processing
     * @param errorMessage  context about the failure
     * @param retryFunction the operation that was attempted (ignored in this implementation)
     * @return the original error object (may be published as a {@code Result} message)
     */
    @Override
    public Object handleError(Throwable error, String errorMessage, Callable<?> retryFunction) {
        if (!(error instanceof FunctionalException)) {
            log.error("{}. Continuing...", errorMessage, error);
        } else if (logFunctionalErrors) {
            log.warn("{}. Continuing...", errorMessage, error);
        }
        return error;
    }
}
