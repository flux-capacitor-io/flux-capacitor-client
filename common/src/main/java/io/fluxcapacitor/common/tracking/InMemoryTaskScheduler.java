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

package io.fluxcapacitor.common.tracking;

import io.fluxcapacitor.common.DirectExecutorService;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.ThrowingRunnable;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.common.TimingUtils.isMissedDeadline;

@Slf4j
public class InMemoryTaskScheduler implements TaskScheduler {
    public static int defaultDelay = 100;
    public static Clock defaultclock = Clock.systemUTC();

    private final ScheduledExecutorService executorService;
    private final ExecutorService workerPool;
    private final Clock clock;
    private final Set<Task> tasks = new CopyOnWriteArraySet<>();

    public InMemoryTaskScheduler() {
        this(defaultclock);
    }

    public InMemoryTaskScheduler(Clock clock) {
        this(defaultDelay, clock);
    }

    public InMemoryTaskScheduler(String threadName) {
        this(threadName, defaultclock);
    }

    public InMemoryTaskScheduler(String threadName, Clock clock) {
        this(defaultDelay, threadName, clock);
    }

    public InMemoryTaskScheduler(String threadName, Clock clock, ExecutorService workerPool) {
        this(defaultDelay, threadName, clock, workerPool);
    }

    public InMemoryTaskScheduler(int delay) {
        this(delay, defaultclock);
    }

    public InMemoryTaskScheduler(int delay, Clock clock) {
        this(delay, "InMemoryTaskScheduler", clock);
    }

    public InMemoryTaskScheduler(int delay, String threadName) {
        this(delay, threadName, defaultclock);
    }

    public InMemoryTaskScheduler(int delay, String threadName, Clock clock) {
        this(delay, threadName, clock, DirectExecutorService.newInstance());
    }

    public InMemoryTaskScheduler(int delay, String threadName, Clock clock, ExecutorService workerPool) {
        this.executorService = Executors.newSingleThreadScheduledExecutor(newThreadFactory(threadName));
        this.workerPool = workerPool;
        this.clock = clock;
        executorService.scheduleWithFixedDelay(this::executeExpiredTasksAsync, delay, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void executeExpiredTasks() {
        tasks.forEach(task -> {
            if (isMissedDeadline(clock(), task.deadline) && tasks.remove(task)) {
                tryRunTask(task);
            }
        });
    }

    public void executeExpiredTasksAsync() {
        tasks.forEach(task -> {
            if (isMissedDeadline(clock(), task.deadline) && tasks.remove(task)) {
                workerPool.submit(() -> tryRunTask(task));
            }
        });
    }

    protected void tryRunTask(Task task) {
        try {
            task.runnable.run();
        } catch (Throwable e) {
            log.error("Failed to execute scheduled task", e);
        }
    }

    @Override
    public void submit(ThrowingRunnable task) {
        TaskScheduler.super.submit(task);
        executorService.submit(this::executeExpiredTasks);
    }

    @Override
    public Registration schedule(long deadline, ThrowingRunnable task) {
        Task schedulerTask = new Task(task, deadline);
        tasks.add(schedulerTask);
        return () -> tasks.remove(schedulerTask);
    }

    @Override
    public Clock clock() {
        return clock;
    }

    @Override
    @SneakyThrows
    public void shutdown() {
        executorService.shutdownNow();
        workerPool.shutdown();
        if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("Failed to shutdown worker pool before finishing all tasks");
        }
    }

    protected static class Task {
        private final ThrowingRunnable runnable;
        private final long deadline;

        public Task(ThrowingRunnable runnable, long deadline) {
            this.runnable = runnable;
            this.deadline = deadline;
        }
    }
}
