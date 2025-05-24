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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple implementation of {@link AbstractExecutorService} that executes tasks directly in the calling thread without
 * using any worker threads.
 * <p>
 * This class facilitates immediate execution of tasks provided to it. It does not queue tasks or execute them
 * asynchronously. Instead, tasks are executed sequentially on the calling thread. This behavior makes it particularly
 * useful in certain testing scenarios or for lightweight use cases where threading control is not necessary.
 * <p>
 * This class maintains an internal state to track whether the executor has been shut down, whether it is terminated,
 * and which tasks are currently running. Once shut down, no new tasks can be executed.
 * <p>
 * Features: - Tasks are executed directly in the calling thread, without any delay or queueing. - Keeps track of
 * running tasks and prevents task execution after the executor has been shut down. - Supports shutting down and forced
 * shutdown via {@link #shutdown()} and {@link #shutdownNow()}. - Provides termination and shutdown status via
 * {@link #isShutdown()} and {@link #isTerminated()}. - Allows the caller to wait for termination using
 * {@link #awaitTermination(long, TimeUnit)}.
 * <p>
 * Thread Safety: Internally uses thread-safe mechanisms such as {@link AtomicBoolean} and {@link CopyOnWriteArrayList}
 * to handle state tracking and concurrent invocations.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DirectExecutorService extends AbstractExecutorService {
    public static ExecutorService newInstance() {
        return new DirectExecutorService();
    }

    private final AtomicBoolean shutDown = new AtomicBoolean();
    private final CopyOnWriteArrayList<Runnable> runningTasks = new CopyOnWriteArrayList<>();

    @Override
    public void execute(@NonNull Runnable command) {
        if (isShutdown()) {
            throw new RejectedExecutionException("Executor was shut down");
        }
        try {
            runningTasks.add(command);
            command.run();
        } finally {
            runningTasks.remove(command);
        }
    }

    @Override
    public void shutdown() {
        shutDown.set(true);
    }

    @Override
    public boolean isShutdown() {
        return shutDown.get();
    }

    @NotNull
    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        return runningTasks;
    }

    @Override
    public boolean isTerminated() {
        return isShutdown() && runningTasks.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (!isTerminated()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            TimeUnit.MILLISECONDS.sleep(Math.min(TimeUnit.NANOSECONDS.toMillis(remaining), 10));
        }
        return true;
    }
}
