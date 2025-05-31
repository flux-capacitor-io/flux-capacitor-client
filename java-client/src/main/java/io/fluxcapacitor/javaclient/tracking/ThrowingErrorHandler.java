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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;

/**
 * An {@link ErrorHandler} implementation that forcefully halts message tracking by throwing any encountered errors.
 * <p>
 * This handler is designed for critical scenarios where continuation after an error is not acceptable, such as:
 * <ul>
 *     <li>Data integrity violations</li>
 *     <li>Irrecoverable technical failures</li>
 *     <li>Strict consistency or audit requirements</li>
 * </ul>
 * <p>
 * Upon encountering an error, this handler logs the issue (if configured to do so) and rethrows the original
 * {@code Throwable}. This causes message tracking to stop until it is explicitly restarted—typically after resolving
 * the failure or redeploying the application.
 *
 * <p><strong>Logging Behavior:</strong>
 * <ul>
 *     <li>Technical exceptions (i.e., not {@link FunctionalException}) are logged if {@code logTechnicalErrors} is {@code true}.</li>
 *     <li>{@link FunctionalException}s are logged if {@code logFunctionalErrors} is {@code true}.</li>
 *     <li>In either case, the log level is {@code ERROR} and includes the full stack trace.</li>
 * </ul>
 *
 * <p><strong>Usage:</strong> Can be registered via {@link Consumer#errorHandler()} or programmatically via {@link ConsumerConfiguration}.
 *
 * <pre>{@code
 * @Consumer(name = "criticalConsumer", errorHandler = ThrowingErrorHandler.class)
 * public class CriticalHandler {
 *     @HandleCommand
 *     void handle(UpdateBankBalance command) {
 *         // Fails fast on any error
 *     }
 * }
 * }</pre>
 *
 * @see ErrorHandler
 * @see FunctionalException
 */
@Slf4j
@AllArgsConstructor
public class ThrowingErrorHandler implements ErrorHandler {

    private final boolean logFunctionalErrors;
    private final boolean logTechnicalErrors;

    /**
     * Constructs a default ThrowingErrorHandler that logs both functional and technical errors.
     */
    public ThrowingErrorHandler() {
        this(true, true);
    }

    /**
     * Handles the given error by optionally logging it and then throwing it to halt message tracking.
     *
     * @param error the {@link Throwable} encountered during message processing
     * @param errorMessage context about the failure
     * @param retryFunction the operation that was attempted (ignored in this implementation)
     * @return never returns normally – always throws the original error
     */
    @Override
    @SneakyThrows
    public Object handleError(Throwable error, String errorMessage, Callable<?> retryFunction) {
        logError(error, errorMessage);
        throw error;
    }

    /**
     * Logs the given error based on its type and logging configuration.
     *
     * @param error the error to log
     * @param errorMessage message to accompany the log entry
     */
    protected void logError(Throwable error, String errorMessage) {
        if (!(error instanceof FunctionalException)) {
            if (logTechnicalErrors) {
                log.error("{}. Propagating error...", errorMessage, error);
            }
        } else if (logFunctionalErrors) {
            log.error("{}. Propagating error...", errorMessage, error);
        }
    }
}
