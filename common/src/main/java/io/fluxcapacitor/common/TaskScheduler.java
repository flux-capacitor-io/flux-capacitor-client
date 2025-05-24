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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Interface for in-memory scheduling of tasks in a way that supports deterministic testing and virtualized time.
 * <p>
 * {@code TaskScheduler} enables deferred execution of tasks based on a deadline or delay. It is used by Flux
 * Capacitor internally, but can also be applied directly for scheduling logic within applications.
 * <p>
 * Unlike typical thread-based scheduling (e.g. {@code ScheduledExecutorService}), this abstraction allows
 * fine-grained control over when tasks are executed, making it ideal for use in test fixtures and simulations
 * where real-time waiting would be undesirable.
 */
public interface TaskScheduler {

    /**
     * Immediately schedules a task for execution.
     *
     * @param task the task to execute
     */
    default void submit(ThrowingRunnable task) {
        schedule(Duration.ZERO, task);
    }

    /**
     * Schedules a task to be executed after a specified delay.
     *
     * @param duration the delay duration after which the task should be executed
     * @param task     the task to execute
     * @return a {@link Registration} that can be used to cancel the task before execution
     */
    default Registration schedule(Duration duration, ThrowingRunnable task) {
        return schedule(clock().instant().plus(duration), task);
    }

    /**
     * Schedules a task to be executed at a specific timestamp.
     *
     * @param deadline the {@link Instant} timestamp indicating when to execute the task
     * @param task     the task to execute
     * @return a {@link Registration} that can be used to cancel the task before execution
     */
    default Registration schedule(Instant deadline, ThrowingRunnable task) {
        return schedule(deadline.toEpochMilli(), task);
    }

    /**
     * Schedules a task to be executed at the given epoch millisecond timestamp.
     *
     * @param deadline epoch milliseconds (UTC) representing the execution time
     * @param task     the task to execute
     * @return a {@link Registration} to cancel the task if needed
     */
    Registration schedule(long deadline, ThrowingRunnable task);

    /**
     * Sets a timeout for a {@link CompletableFuture}. If the future does not complete before the timeout expires,
     * it is completed exceptionally with a {@link TimeoutException}.
     * <p>
     * The returned future will still complete normally if the underlying task completes first.
     *
     * @param future  the future to observe
     * @param timeout the maximum duration to wait before timing out
     * @param <R>     the type of the result
     * @return the same future instance
     */
    default <R> CompletableFuture<R> orTimeout(CompletableFuture<R> future, Duration timeout) {
        if (!future.isDone()) {
            Registration r = schedule(timeout, () -> future.completeExceptionally(new TimeoutException()));
            future.whenComplete((__, e) -> r.cancel());
        }
        return future;
    }

    /**
     * Returns the clock associated with this scheduler. This clock is used to determine the current time for scheduling.
     * <p>
     * In test scenarios, the clock may be a mock or virtualized instance to support deterministic test behavior.
     *
     * @return the {@link Clock} used by this scheduler
     */
    Clock clock();

    /**
     * Executes any scheduled tasks that are due according to the scheduler's current time.
     * <p>
     * Useful for test scenarios or manual triggering of scheduled behavior.
     */
    void executeExpiredTasks();

    /**
     * Shuts down the scheduler and stops execution of any pending or future tasks.
     * <p>
     * In production use, this ensures proper resource cleanup. In test environments, this may reset scheduler state.
     */
    void shutdown();
}
