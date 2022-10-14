/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static java.lang.String.format;

@Slf4j
@AllArgsConstructor
public class RetryingErrorHandler implements ErrorHandler {
    private final int retries;
    private final Duration delay;
    private final Predicate<Exception> errorFilter;
    private final boolean throwOnFailure;
    private final boolean logFunctionalErrors;

    public RetryingErrorHandler() {
        this(false);
    }

    public RetryingErrorHandler(boolean throwOnFailure) {
        this(e -> !(e instanceof FunctionalException), throwOnFailure);
    }

    public RetryingErrorHandler(Predicate<Exception> errorFilter) {
        this(errorFilter, false);
    }

    public RetryingErrorHandler(Predicate<Exception> errorFilter, boolean throwOnFailure) {
        this(5, Duration.ofSeconds(2), errorFilter, throwOnFailure, true);
    }

    @Override
    public void handleError(Exception error, String errorMessage, Runnable retryFunction) throws Exception {
        if (!errorFilter.test(error)) {
            logError(format("%s. Not retrying. %s", errorMessage, throwOnFailure
                    ? "Propagating error." : "Continuing with next handler."), error);
            if (throwOnFailure) {
                throw error;
            }
            return;
        }

        if (retries > 0) {
            log.warn("{}. Retrying up to {} times.", errorMessage, retries, error);
        } else {
            log.warn("{}. Retrying until the errors stop.", errorMessage, error);
        }
        AtomicInteger remainingRetries = new AtomicInteger(retries);
        boolean success = retryOnFailure(retryFunction, delay,
                                         e -> errorFilter.test(e)
                                              && (retries <= 0 || remainingRetries.decrementAndGet() > 0));
        if (success) {
            log.info("Message handling was successful on retry");
        } else {
            if (throwOnFailure) {
                log.error("{}. Not retrying any further. Propagating error.", errorMessage, error);
                throw error;
            } else {
                logError(errorMessage + ". Not retrying any further. Continuing with next handler.", error);
            }
        }
    }

    private void logError(String message, Exception error) {
        if (isTechnicalError(error)) {
            log.error(message, error);
        } else if (logFunctionalErrors) {
            log.warn(message, error);
        }
    }

    protected void logWarning(String errorMessage, Exception error) {
        if (isTechnicalError(error)) {
            log.error("{}", errorMessage, error);
        } else if (logFunctionalErrors) {
            log.warn("{}", errorMessage, error);
        }
    }

    protected boolean isTechnicalError(Exception error) {
        return !(error instanceof FunctionalException);
    }
}
