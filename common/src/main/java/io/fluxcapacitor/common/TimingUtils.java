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

/**
 * Utility class for measuring execution time and retrying operations with configurable backoff and error handling.
 * <p>
 * Provides static methods to:
 * <ul>
 *     <li>Measure the execution duration of tasks with optional reporting callbacks.</li>
 *     <li>Retry failing tasks with custom retry conditions and backoff strategies.</li>
 *     <li>Check if a deadline has passed.</li>
 * </ul>
 */
@Slf4j
public class TimingUtils {

    /**
     * Executes a task and measures its execution time in milliseconds.
     *
     * @param task     the task to run
     * @param callback the callback to report the elapsed time
     */
    public static void time(Runnable task, Consumer<Long> callback) {
        time(() -> {
            task.run();
            return null;
        }, callback);
    }

    /**
     * Executes a task and measures its execution time in the specified {@link TemporalUnit}.
     *
     * @param task     the task to run
     * @param callback the callback to report the elapsed time
     * @param timeUnit the unit of time to report
     */
    public static void time(Runnable task, Consumer<Long> callback, TemporalUnit timeUnit) {
        time(() -> {
            task.run();
            return null;
        }, callback, timeUnit);
    }

    /**
     * Executes a {@link Callable} and measures its execution time in milliseconds.
     *
     * @param task     the task to run
     * @param callback the callback to report the elapsed time
     * @param <T>      the return type of the task
     * @return the result of the callable
     */
    public static <T> T time(Callable<T> task, Consumer<Long> callback) {
        return time(task, callback, ChronoUnit.MILLIS);
    }

    /**
     * Executes a {@link Callable} and measures its execution time in the given {@link TemporalUnit}.
     *
     * @param task     the task to run
     * @param callback the callback to report the elapsed time
     * @param timeUnit the time unit to use
     * @param <T>      the return type of the task
     * @return the result of the callable
     */
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

    /**
     * Retries a task indefinitely if it fails, using a fixed delay.
     *
     * @param task  the task to retry
     * @param delay the delay between retries
     */
    public static void retryOnFailure(Runnable task, Duration delay) {
        retryOnFailure(task, delay, e -> true);
    }

    /**
     * Retries a task indefinitely if it fails and matches the given exception predicate.
     *
     * @param task      the task to retry
     * @param delay     the delay between retries
     * @param predicate predicate to determine which exceptions are retryable
     */
    public static void retryOnFailure(Runnable task, Duration delay, Predicate<Exception> predicate) {
        retryOnFailure(() -> {
            task.run();
            return new Object();
        }, delay, predicate);
    }

    /**
     * Retries a {@link Callable} task indefinitely with a fixed delay.
     *
     * @param task  the task to retry
     * @param delay the delay between retries
     * @param <T>   the result type
     * @return the successful result
     */
    public static <T> T retryOnFailure(Callable<T> task, Duration delay) {
        return retryOnFailure(task, delay, e -> true);
    }

    /**
     * Retries a {@link Callable} task with a delay and a predicate to filter retryable exceptions.
     *
     * @param task      the task to execute
     * @param delay     the delay between retries
     * @param errorTest predicate to test whether an exception is retryable
     * @param <T>       the result type
     * @return the successful result, or {@code null} if not retryable and throwOnFailingErrorTest is false
     */
    public static <T> T retryOnFailure(Callable<T> task, Duration delay, Predicate<Exception> errorTest) {
        return retryOnFailure(task, RetryConfiguration.builder().delay(delay).errorTest(errorTest).build());
    }

    /**
     * Retries a {@link Callable} task using a full {@link RetryConfiguration}. Supports optional logging, error test
     * logic, retry limits, and fail-fast behavior.
     *
     * @param task          the task to run
     * @param configuration the retry configuration
     * @param <T>           the result type
     * @return the result of the task
     */
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

    /**
     * Returns whether the given deadline has passed using the system UTC clock.
     *
     * @param deadline the deadline to compare
     * @return true if the current time is after the deadline
     */
    public static boolean isMissedDeadline(long deadline) {
        return isMissedDeadline(Clock.systemUTC(), deadline);
    }

    /**
     * Returns whether the given deadline has passed using the specified {@link Clock}.
     *
     * @param clock    the clock to use
     * @param deadline the deadline to compare
     * @return true if the current time is after the deadline
     */
    public static boolean isMissedDeadline(Clock clock, long deadline) {
        return clock.millis() >= deadline;
    }
}
