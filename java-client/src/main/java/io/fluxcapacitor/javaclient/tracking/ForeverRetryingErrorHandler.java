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

import io.fluxcapacitor.common.RetryConfiguration;
import io.fluxcapacitor.javaclient.common.exception.FunctionalException;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A specialized {@link RetryingErrorHandler} that retries failed operations indefinitely until they succeed.
 * <p>
 * This handler is useful in scenarios where failure is considered temporary and must eventually resolve before
 * processing can proceed. It ensures **no message is ever skipped or dropped**, regardless of how many attempts
 * are required.
 *
 * <p><strong>Behavior:</strong>
 * <ul>
 *     <li>Applies retry logic to all errors that match the provided {@code errorFilter} (default: non-functional errors).</li>
 *     <li>Waits a fixed {@link Duration} between attempts (default: 10 seconds).</li>
 *     <li>Never stops the consumer or throws — tracking continues until the retry succeeds.</li>
 *     <li>Logs retry attempts and outcomes via inherited {@link RetryConfiguration}.</li>
 * </ul>
 *
 * <p><strong>Use Cases:</strong>
 * <ul>
 *     <li>Systems that rely on eventual consistency and cannot tolerate message loss</li>
 *     <li>Recoverable infrastructure failures (e.g., DB outages, network timeouts)</li>
 *     <li>Replaying old messages into strict projections</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * @Consumer(name = "criticalProjection", errorHandler = ForeverRetryingErrorHandler.class)
 * public class StrictProjectionHandler {
 *     @HandleEvent
 *     void on(FinancialTransaction event) {
 *         // Will retry forever on failure until the event is handled successfully
 *     }
 * }
 * }</pre>
 *
 * @see RetryingErrorHandler
 * @see ErrorHandler
 * @see FunctionalException
 */
public class ForeverRetryingErrorHandler extends RetryingErrorHandler {

    /**
     * Constructs a {@code ForeverRetryingErrorHandler} with a default 10-second delay between attempts,
     * retrying non-functional errors and logging both functional and technical failures.
     */
    public ForeverRetryingErrorHandler() {
        this(Duration.ofSeconds(10), e -> !(e instanceof FunctionalException), true, e -> e);
    }

    /**
     * Constructs a {@code ForeverRetryingErrorHandler} with custom delay, error filtering, logging, and error mapping.
     *
     * @param delay               the delay between retries
     * @param errorFilter         predicate to select which errors should trigger retries
     * @param logFunctionalErrors whether to log functional errors
     * @param errorMapper         maps the final error into a result (though retries never exhaust)
     */
    public ForeverRetryingErrorHandler(Duration delay, Predicate<Throwable> errorFilter, boolean logFunctionalErrors,
                                       Function<Throwable, ?> errorMapper) {
        super(-1, delay, errorFilter, false, logFunctionalErrors, errorMapper);
    }
}
