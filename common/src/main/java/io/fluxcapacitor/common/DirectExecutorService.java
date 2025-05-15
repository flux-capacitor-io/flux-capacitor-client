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
