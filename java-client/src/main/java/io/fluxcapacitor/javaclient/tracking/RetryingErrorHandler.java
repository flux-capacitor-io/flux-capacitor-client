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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static java.lang.String.format;

/**
 * An {@link ErrorHandler} implementation that retries failed operations a configurable number of times, with optional
 * propagation or suppression of unrecoverable errors.
 * <p>
 * This handler is designed for scenarios where transient failures (e.g., network hiccups, temporary service downtime)
 * are expected and recovery is possible through retrying the operation. It allows for detailed control over:
 * <ul>
 *     <li>Which errors should trigger retries (via {@code errorFilter})</li>
 *     <li>How many retries to perform and at what delay (via {@link RetryConfiguration})</li>
 *     <li>Whether to stop the consumer or continue on final failure</li>
 *     <li>Whether functional errors should be logged</li>
 * </ul>
 *
 * <p><strong>Retry Logic:</strong>
 * <ul>
 *     <li>If the error matches {@code errorFilter}, the {@code retryFunction} is invoked repeatedly up to {@code maxRetries}.</li>
 *     <li>If all retries fail:
 *       <ul>
 *         <li>And {@code stopConsumerOnFailure} is {@code true}, the original error is rethrown, halting tracking.</li>
 *         <li>Otherwise, the error is logged and passed through {@link RetryConfiguration#getErrorMapper()}.</li>
 *       </ul>
 *     </li>
 *     <li>If the error does not match the filter, no retries are performed and the handler either continues or propagates, based on configuration.</li>
 * </ul>
 *
 * <p><strong>Logging Behavior:</strong>
 * <ul>
 *     <li>Technical errors are logged at {@code ERROR} level.</li>
 *     <li>{@link FunctionalException}s are logged at {@code WARN} level, if {@code logFunctionalErrors} is {@code true}.</li>
 *     <li>Retry success is logged via {@code RetryConfiguration.successLogger}.</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * @Consumer(name = "resilientHandler", errorHandler = RetryingErrorHandler.class)
 * public class ResilientCommandHandler {
 *     @HandleCommand
 *     void handle(PlaceOrder command) {
 *         // Retries on technical failures before giving up
 *     }
 * }
 * }</pre>
 *
 * @see ErrorHandler
 * @see RetryConfiguration
 * @see FunctionalException
 * @see ThrowingErrorHandler
 * @see LoggingErrorHandler
 */
@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class RetryingErrorHandler implements ErrorHandler {

    /**
     * Predicate to determine which errors should trigger retries. For example, ignoring {@link FunctionalException}.
     */
    private final Predicate<Throwable> errorFilter;

    /**
     * Whether to propagate the error and stop the consumer if retries are exhausted or skipped.
     */
    private final boolean stopConsumerOnFailure;

    /**
     * Whether to log functional (business) exceptions at WARN level.
     */
    private final boolean logFunctionalErrors;

    /**
     * Retry policy and associated logging behavior.
     */
    private final RetryConfiguration retryConfiguration;

    /**
     * Constructs a handler that retries on technical exceptions up to 5 times with a 2-second delay. Consumer is not
     * stopped on failure.
     */
    public RetryingErrorHandler() {
        this(false);
    }

    /**
     * Constructs a handler with default retry behavior and optional consumer stop behavior.
     */
    public RetryingErrorHandler(boolean stopConsumerOnFailure) {
        this(e -> !(e instanceof FunctionalException), stopConsumerOnFailure);
    }

    /**
     * Constructs a handler with a custom error filter that allows message tracking to continue if the error persists
     * after retries.
     */
    public RetryingErrorHandler(Predicate<Throwable> errorFilter) {
        this(errorFilter, false);
    }

    /**
     * Constructs a handler with a custom error filter and optional consumer stop behavior.
     */
    public RetryingErrorHandler(Predicate<Throwable> errorFilter, boolean stopConsumerOnFailure) {
        this(5, Duration.ofSeconds(2), errorFilter, stopConsumerOnFailure, true);
    }

    /**
     * Constructs a handler with detailed retry configuration options.
     */
    public RetryingErrorHandler(int maxRetries, Duration delay, Predicate<Throwable> errorFilter,
                                boolean stopConsumerOnFailure, boolean logFunctionalErrors) {
        this(maxRetries, delay, errorFilter, stopConsumerOnFailure, logFunctionalErrors, e -> e);
    }

    /**
     * Constructs a fully customized retrying handler with retry behavior and error mapping logic.
     */
    public RetryingErrorHandler(int maxRetries, Duration delay, Predicate<Throwable> errorFilter,
                                boolean stopConsumerOnFailure, boolean logFunctionalErrors,
                                Function<Throwable, ?> errorMapper) {
        this(errorFilter, stopConsumerOnFailure, logFunctionalErrors,
             RetryConfiguration.builder().delay(delay).maxRetries(maxRetries).errorMapper(errorMapper)
                     .successLogger(s -> log.info("Message handling was successful on retry"))
                     .exceptionLogger(s -> {}).build());
    }

    /**
     * Handles the error by retrying the operation if allowed, or propagating/logging based on configuration. Throws the
     * original error if retries are exhausted and the tracker should stop.
     *
     * @param error         the encountered {@link Throwable}
     * @param errorMessage  context about the failure
     * @param retryFunction the operation that failed
     * @return the final result or error after retries
     */
    @Override
    @SneakyThrows
    public Object handleError(Throwable error, String errorMessage, Callable<?> retryFunction) {
        if (!errorFilter.test(error)) {
            logError(format("%s. Not retrying. %s", errorMessage, stopConsumerOnFailure
                    ? "Propagating error." : "Continuing with next handler."), error);
            if (stopConsumerOnFailure) {
                throw error;
            }
            return retryConfiguration.getErrorMapper().apply(error);
        }

        try {
            if (retryConfiguration.getMaxRetries() == 0) {
                throw error;
            } else if (retryConfiguration.getMaxRetries() > 0) {
                log.warn("{}. Retrying up to {} times.", errorMessage, retryConfiguration.getMaxRetries(), error);
            } else {
                log.warn("{}. Retrying until the errors stop.", errorMessage, error);
            }
            return retryOnFailure(retryFunction, retryConfiguration);
        } catch (Throwable e) {
            if (stopConsumerOnFailure) {
                log.error("{}. Not retrying any further. Propagating error.", errorMessage, error);
                throw error;
            } else {
                logError(errorMessage + ". Not retrying any further. Continuing with next handler.", error);
            }
            return retryConfiguration.getErrorMapper().apply(error);
        }
    }

    /**
     * Logs the error at the appropriate level based on its type.
     */
    protected void logError(String message, Throwable error) {
        if (isTechnicalError(error)) {
            log.error(message, error);
        } else if (logFunctionalErrors) {
            log.warn(message, error);
        }
    }

    /**
     * Determines if the error is a technical exception (not {@link FunctionalException}).
     */
    protected static boolean isTechnicalError(Throwable error) {
        return !(error instanceof FunctionalException);
    }
}
