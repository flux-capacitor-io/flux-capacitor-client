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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;

import java.util.concurrent.Callable;

/**
 * An {@link ErrorHandler} implementation that suppresses all processing errors and allows message tracking to
 * continue.
 * <p>
 * This handler is ideal for non-critical consumers where message loss or failure should not disrupt the application,
 * such as:
 * <ul>
 *     <li>Metrics collection</li>
 *     <li>Auditing or logging projections</li>
 *     <li>Replays for observability/debugging</li>
 * </ul>
 *
 * <p><strong>Logging Behavior:</strong>
 * <ul>
 *     <li>If {@link #maxLevel} is specified, all errors are logged at that level (e.g., {@code WARN}, {@code DEBUG}).</li>
 *     <li>If {@code maxLevel} is {@code null}, errors are completely silent—nothing is logged.</li>
 *     <li>All logs include the error context and exception stack trace (if any).</li>
 * </ul>
 *
 * <p><strong>Control Flow:</strong>
 * <ul>
 *     <li>The error is returned as-is, meaning it may be published as a {@code Result} message but does not halt processing.</li>
 *     <li>The original {@code retryFunction} is not invoked.</li>
 *     <li>No exceptions are thrown—tracking always continues.</li>
 * </ul>
 *
 * <p><strong>Usage:</strong> Can be registered via {@link Consumer#errorHandler()} or programmatically via {@link ConsumerConfiguration}.
 *
 * <pre>{@code
 * @Consumer(name = "audit", errorHandler = SilentErrorHandler.class)
 * public class AuditProjection {
 *     @HandleEvent
 *     void on(UserLoggedIn event) {
 *         // Failure to write to audit log won't affect tracking
 *     }
 * }
 * }</pre>
 *
 * @see ErrorHandler
 * @see ThrowingErrorHandler
 */
@Slf4j
@AllArgsConstructor
public class SilentErrorHandler implements ErrorHandler {

    /**
     * The maximum logging level to use when an error occurs. If {@code null}, no logging is performed.
     */
    private final Level maxLevel;

    /**
     * Constructs a {@code SilentErrorHandler} that does not log any errors.
     */
    public SilentErrorHandler() {
        this(null);
    }

    /**
     * Handles the given error by optionally logging it and then returning it without retry or propagation.
     *
     * @param error         the {@link Throwable} encountered during message processing
     * @param errorMessage  context about the failure
     * @param retryFunction the operation that was attempted (ignored in this implementation)
     * @return the original error object (may be published as a {@code Result} message)
     */
    @Override
    public Object handleError(Throwable error, String errorMessage, Callable<?> retryFunction) {
        if (maxLevel != null) {
            log.atLevel(maxLevel).log("{}. Continuing...", errorMessage, error);
        }
        return error;
    }
}
