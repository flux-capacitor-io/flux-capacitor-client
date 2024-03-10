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

import io.fluxcapacitor.common.Registration;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.common.TimingUtils.isMissedDeadline;

@Slf4j
public class InMemoryTaskScheduler implements TaskScheduler {
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(
            newThreadFactory("InMemoryScheduler"));
    private final Set<Task> tasks = new CopyOnWriteArraySet<>();

    public InMemoryTaskScheduler() {
        this(100);
    }

    public InMemoryTaskScheduler(int delay) {
        executorService.scheduleWithFixedDelay(this::executeExpiredTasks, delay, delay, TimeUnit.MILLISECONDS);
    }

    private void executeExpiredTasks() {
        tasks.forEach(task -> {
            if (isMissedDeadline(task.deadline) && tasks.remove(task)) {
                try {
                    task.runnable.run();
                } catch (Exception e) {
                    log.error("Failed to execute scheduled task", e);
                }
            }
        });
    }

    @Override
    public Registration schedule(long deadline, Runnable task) {
        Task schedulerTask = new Task(task, deadline);
        tasks.add(schedulerTask);
        return () -> tasks.remove(schedulerTask);
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    private static class Task {
        private final Runnable runnable;
        private final long deadline;

        public Task(Runnable runnable, long deadline) {
            this.runnable = runnable;
            this.deadline = deadline;
        }
    }
}
