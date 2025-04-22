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

package io.fluxcapacitor.common;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.lang.Thread.currentThread;

@Slf4j
public class TimingUtils {

    public static void time(Runnable task, Consumer<Long> callback) {
        time(() -> {
            task.run();
            return null;
        }, callback);
    }

    public static void time(Runnable task, Consumer<Long> callback, TemporalUnit timeUnit) {
        time(() -> {
            task.run();
            return null;
        }, callback, timeUnit);
    }

    public static <T> T time(Callable<T> task, Consumer<Long> callback) {
        return time(task, callback, ChronoUnit.MILLIS);
    }

    public static <T> T time(Callable<T> task, Consumer<Long> callback, TemporalUnit timeUnit) {
        long start = System.nanoTime();
        try {
            return task.call();
        } catch (Exception e) {
            throw new IllegalStateException("Task failed to execute", e);
        } finally {
            long elapsedNanos = System.nanoTime() - start;
            callback.accept(elapsedNanos / timeUnit.getDuration().toNanos());
        }
    }

    public static void retryOnFailure(Runnable task, Duration delay) {
        retryOnFailure(task, delay, e -> true);
    }

    public static void retryOnFailure(Runnable task, Duration delay, Predicate<Exception> predicate) {
        retryOnFailure(() -> {
            task.run();
            return new Object();
        }, delay, predicate);
    }

    public static <T> T retryOnFailure(Callable<T> task, Duration delay) {
        return retryOnFailure(task, delay, e -> true);
    }

    public static <T> T retryOnFailure(Callable<T> task, Duration delay, Predicate<Exception> errorTest) {
        return retryOnFailure(task, RetryConfiguration.builder().delay(delay).errorTest(errorTest).build());
    }

    @SuppressWarnings("BusyWait")
    @SneakyThrows
    public static <T> T retryOnFailure(Callable<T> task, RetryConfiguration configuration) {
        RetryStatus retryStatus = null;
        while (true) {
            try {
                T result = task.call();
                if (result instanceof CompletableFuture<?>) {
                    ((CompletableFuture<?>) result).get();
                }
                if (retryStatus != null) {
                    configuration.getSuccessLogger().accept(retryStatus);
                }
                return result;
            } catch (Exception e) {
                retryStatus = retryStatus == null ?
                        RetryStatus.builder().retryConfiguration(configuration).exception(e).task(task).build() :
                        retryStatus.afterRetry(e);
                if (!configuration.getErrorTest().test(e)) {
                    if (configuration.isThrowOnFailingErrorTest()) {
                        throw retryStatus.getException();
                    }
                    return null;
                }
                configuration.getExceptionLogger().accept(retryStatus);
                if (configuration.getMaxRetries() >= 0
                        && retryStatus.getNumberOfTimesRetried() >= configuration.getMaxRetries()) {
                    break;
                }
                try {
                    Thread.sleep(configuration.getDelay().toMillis());
                } catch (InterruptedException e1) {
                    currentThread().interrupt();
                    log.info("Thread interrupted while retrying task {}", task, e1);
                    break;
                }
            } catch (Error e) {
                log.error("Task {} failed with error. Will not retry.", task, e);
                throw e;
            }
        }
        throw retryStatus.getException();
    }

    public static boolean isMissedDeadline(long deadline) {
        return isMissedDeadline(Clock.systemUTC(), deadline);
    }

    public static boolean isMissedDeadline(Clock clock, long deadline) {
        return clock.millis() >= deadline;
    }
}
