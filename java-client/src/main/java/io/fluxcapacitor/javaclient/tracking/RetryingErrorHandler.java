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

@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class RetryingErrorHandler implements ErrorHandler {
    private final Predicate<Throwable> errorFilter;
    private final boolean stopConsumerOnFailure;
    private final boolean logFunctionalErrors;
    private final RetryConfiguration retryConfiguration;

    public RetryingErrorHandler() {
        this(false);
    }

    public RetryingErrorHandler(boolean stopConsumerOnFailure) {
        this(e -> !(e instanceof FunctionalException), stopConsumerOnFailure);
    }

    public RetryingErrorHandler(Predicate<Throwable> errorFilter) {
        this(errorFilter, false);
    }

    public RetryingErrorHandler(Predicate<Throwable> errorFilter, boolean stopConsumerOnFailure) {
        this(5, Duration.ofSeconds(2), errorFilter, stopConsumerOnFailure, true);
    }

    public RetryingErrorHandler(int maxRetries, Duration delay, Predicate<Throwable> errorFilter,
                                boolean stopConsumerOnFailure, boolean logFunctionalErrors) {
        this(maxRetries, delay, errorFilter, stopConsumerOnFailure, logFunctionalErrors, e -> e);
    }

    public RetryingErrorHandler(int maxRetries, Duration delay, Predicate<Throwable> errorFilter,
                                boolean stopConsumerOnFailure, boolean logFunctionalErrors,
                                Function<Throwable, ?> errorMapper) {
        this(errorFilter, stopConsumerOnFailure, logFunctionalErrors,
             RetryConfiguration.builder().delay(delay).maxRetries(maxRetries).errorMapper(errorMapper)
                     .successLogger(s -> log.info("Message handling was successful on retry"))
                     .exceptionLogger(s -> {}).build());
    }

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

    protected void logError(String message, Throwable error) {
        if (isTechnicalError(error)) {
            log.error(message, error);
        } else if (logFunctionalErrors) {
            log.warn(message, error);
        }
    }

    protected boolean isTechnicalError(Throwable error) {
        return !(error instanceof FunctionalException);
    }
}
