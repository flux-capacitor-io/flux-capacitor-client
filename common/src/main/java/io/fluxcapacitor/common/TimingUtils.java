/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static java.lang.Thread.currentThread;

@Slf4j
public class TimingUtils {

    public static void time(Runnable task, Consumer<Long> callback) {
        long start = System.currentTimeMillis();
        try {
            task.run();
        } catch (Exception e) {
            throw new IllegalStateException("Task failed to execute", e);
        }
        callback.accept(System.currentTimeMillis() - start);
    }

    public static <T> T time(Callable<T> task, Consumer<Long> callback) {
        long start = System.currentTimeMillis();
        try {
            return task.call();
        } catch (Exception e) {
            throw new IllegalStateException("Task failed to execute", e);
        } finally {
            callback.accept(System.currentTimeMillis() - start);
        }
    }

    public static boolean retryOnFailure(Runnable task, Duration delay) {
        return retryOnFailure(task, delay, e -> true);
    }

    public static <T> T retryOnFailure(Callable<T> task, Duration delay) {
        return retryOnFailure(task, delay, e -> true);
    }

    public static boolean retryOnFailure(Runnable task, Duration delay, Predicate<Exception> predicate) {
        Object result = retryOnFailure(() -> {
            task.run();
            return new Object();
        }, delay, predicate);
        return result != null;
    }

    public static <T> T retryOnFailure(Callable<T> task, Duration delay, Predicate<Exception> predicate) {
        T result = null;
        boolean retrying = false;
        while (result == null) {
            try {
                result = task.call();
                if (retrying) {
                    log.info("Task {} completed successfully on retry", task);
                }
                return result;
            } catch (Exception e) {
                if (!predicate.test(e)) {
                    break;
                }
                if (!retrying) {
                    log.error("Task {} failed. retrying every {} ms...", task, delay.toMillis(), e);
                    retrying = true;
                }
                try {
                    Thread.sleep(delay.toMillis());
                } catch (InterruptedException e1) {
                    currentThread().interrupt();
                    log.info("Thread interrupted while retrying task {}", task);
                    break;
                }
            } catch (Error e) {
                log.error("Task {} failed with error. Will not retry.", task, e);
                throw e;
            }
        }
        return null;
    }

    public static boolean isMissedDeadline(long deadline) {
        return System.currentTimeMillis() > deadline;
    }
}
