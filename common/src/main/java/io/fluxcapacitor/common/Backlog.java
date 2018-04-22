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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

@Slf4j
public class Backlog<T> implements Monitored<List<T>> {

    private final int maxBatchSize;
    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final BatchConsumer<T> consumer;
    private final ErrorHandler<List<T>> errorHandler;
    private final ExecutorService executorService;
    private final AtomicBoolean flushing = new AtomicBoolean();

    private final AtomicLong insertPosition = new AtomicLong();
    private final AtomicLong flushPosition = new AtomicLong();
    private final AtomicReference<Awaitable> syncObject = new AtomicReference<>();

    private final Collection<Consumer<List<T>>> monitors = new CopyOnWriteArraySet<>();

    public Backlog(BatchConsumer<T> consumer) {
        this(consumer, 1024);
    }

    public Backlog(BatchConsumer<T> consumer, int maxBatchSize) {
        this(consumer, maxBatchSize, 1,
             (e, batch) -> log.error("Consumer {} failed to handle batch {}. Continuing with next batch.", consumer, batch, e));
    }

    public Backlog(BatchConsumer<T> consumer, int maxBatchSize, int threads, ErrorHandler<List<T>> errorHandler) {
        this.maxBatchSize = maxBatchSize;
        this.consumer = consumer;
        this.executorService = Executors.newFixedThreadPool(threads);
        this.errorHandler = errorHandler;

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    @SafeVarargs
    public final Awaitable add(T... values) {
        Collections.addAll(queue, values);
        return awaitFlush(insertPosition.updateAndGet(p -> p + values.length));
    }

    public Awaitable add(Collection<? extends T> values) {
        queue.addAll(values);
        return awaitFlush(insertPosition.updateAndGet(p -> p + values.size()));
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            currentThread().interrupt();
            log.error("Shutdown of backlog executor was interrupted", e);
        }
    }

    private Awaitable awaitFlush(long position) {
        flushIfNotFlushing();
        return () -> {
            synchronized (syncObject) {
                while (position > flushPosition.get()) {
                    syncObject.wait();
                }
                syncObject.get().await();
            }
        };
    }

    private void flushIfNotFlushing() {
        if (flushing.compareAndSet(false, true)) {
            executorService.submit(this::flush);
        }
    }

    private void flush() {
        while (!queue.isEmpty()) {
            List<T> batch = new ArrayList<>(maxBatchSize);
            while (batch.size() < maxBatchSize) {
                T value = queue.poll();
                if (value == null) {
                    break;
                }
                batch.add(value);
            }
            Awaitable awaitable;
            try {
                awaitable = consumer.accept(batch);
            } catch (Exception e) {
                awaitable = Awaitable.failed(e);
                errorHandler.handleError(e, batch);
            }
            flushPosition.addAndGet(batch.size());
            synchronized (syncObject) {
                syncObject.set(awaitable);
                syncObject.notifyAll();
            }
            monitors.forEach(m -> m.accept(batch));
        }
        flushing.set(false);
        if (!queue.isEmpty()) { //a value could've been added after the while loop before flushing was set to false
            flushIfNotFlushing();
        }
    }

    @Override
    public Registration registerMonitor(Consumer<List<T>> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }

    @FunctionalInterface
    public interface BatchConsumer<T> {
        Awaitable accept(List<T> batch) throws Exception;
    }
}
