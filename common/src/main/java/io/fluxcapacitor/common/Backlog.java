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

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * A thread-safe batching queue that asynchronously flushes its content to a consumer in configurable batch sizes.
 * <p>
 * This utility is useful for scenarios where multiple values are being added over time and you want to consume
 * them in batches for efficiency—such as sending messages to a remote system, writing to a log, etc.
 *
 * <p>
 * Flushes are executed on a single background thread, and results (e.g. completion or failure) are tracked
 * via {@link CompletableFuture}s. Optional monitors may observe each flushed batch.
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Supports both synchronous and asynchronous consumers</li>
 *   <li>Flushes automatically after new items are added</li>
 *   <li>Tracks flush progress with {@link CompletableFuture} per add</li>
 *   <li>Customizable error handling via {@link ErrorHandler}</li>
 *   <li>Monitoring support via {@link Monitored}</li>
 * </ul>
 *
 * <h2>Typical Use</h2>
 * <pre>{@code
 * Backlog<String> backlog = Backlog.forAsyncConsumer(batch -> {
 *     return sendToServer(batch); // returns CompletableFuture
 * });
 * backlog.add("a", "b", "c");
 * }</pre>
 *
 * @param <T> The type of item being buffered and processed.
 */
@Slf4j
public class Backlog<T> implements Monitored<List<T>> {

    private final int maxBatchSize;
    private final Queue<T> queue = new ConcurrentLinkedQueue<>();
    private final ThrowingFunction<List<T>, CompletableFuture<?>> consumer;
    private final ErrorHandler<List<T>> errorHandler;
    private final ExecutorService executorService;
    private final AtomicBoolean flushing = new AtomicBoolean();

    private final AtomicLong insertPosition = new AtomicLong();
    private final AtomicLong flushPosition = new AtomicLong();

    private final ConcurrentSkipListMap<Long, CompletableFuture<Void>> results = new ConcurrentSkipListMap<>();

    private final Collection<Consumer<List<T>>> monitors = new CopyOnWriteArraySet<>();

    /**
     * Creates a new backlog for a synchronous consumer and default batch size and default logging error handler.
     */
    public static <T> Backlog<T> forConsumer(ThrowingConsumer<List<T>> consumer) {
        return forConsumer(consumer, 1024);
    }

    /**
     * Creates a backlog with custom max batch size and default logging error handler.
     */
    public static <T> Backlog<T> forConsumer(ThrowingConsumer<List<T>> consumer, int maxBatchSize) {
        return forConsumer(consumer, maxBatchSize, (e, batch) -> log.error("Consumer {} failed to handle batch of size {}. Continuing with next batch.", consumer, batch.size(), e));
    }

    /**
     * Creates a backlog with custom max batch size and error handler.
     */
    public static <T> Backlog<T> forConsumer(ThrowingConsumer<List<T>> consumer, int maxBatchSize, ErrorHandler<List<T>> errorHandler) {
        return new Backlog<>(list -> {
            consumer.accept(list);
            return null;
        }, maxBatchSize, errorHandler);
    }

    /**
     * Creates a backlog for an asynchronous consumer with default max batch size and default logging error handler.
     */
    public static <T> Backlog<T> forAsyncConsumer(ThrowingFunction<List<T>, CompletableFuture<?>> consumer) {
        return forAsyncConsumer(consumer, 1024);
    }

    /**
     * Creates a backlog for an asynchronous consumer with custom max batch size and default logging error handler.
     */
    public static <T> Backlog<T> forAsyncConsumer(ThrowingFunction<List<T>, CompletableFuture<?>> consumer, int maxBatchSize) {
        return forAsyncConsumer(consumer, maxBatchSize, (e, batch) -> log.error("Consumer {} failed to handle batch of size {}. Continuing with next batch.", consumer, batch.size(), e));
    }

    /**
     * Creates a backlog for an asynchronous consumer with custom max batch size and error handler.
     */
    public static <T> Backlog<T> forAsyncConsumer(ThrowingFunction<List<T>, CompletableFuture<?>> consumer, int maxBatchSize, ErrorHandler<List<T>> errorHandler) {
        return new Backlog<>(consumer, maxBatchSize, errorHandler);
    }

    protected Backlog(ThrowingFunction<List<T>, CompletableFuture<?>> consumer) {
        this(consumer, 1024);
    }

    protected Backlog(ThrowingFunction<List<T>, CompletableFuture<?>> consumer, int maxBatchSize) {
        this(consumer, maxBatchSize,
             (e, batch) -> log.error("Consumer {} failed to handle batch {}. Continuing with next batch.", consumer, batch, e));
    }

    protected Backlog(ThrowingFunction<List<T>, CompletableFuture<?>> consumer, int maxBatchSize, ErrorHandler<List<T>> errorHandler) {
        this.maxBatchSize = maxBatchSize;
        this.consumer = consumer;
        this.executorService = Executors.newSingleThreadExecutor(newThreadFactory("Backlog"));
        this.errorHandler = errorHandler;
    }

    /**
     * Adds values to the backlog.
     *
     * @param values one or more values to enqueue
     * @return a future that completes when the values are processed by the consumer.
     */
    @SafeVarargs
    public final CompletableFuture<Void> add(T... values) {
        Collections.addAll(queue, values);
        return values.length == 0 ? CompletableFuture.completedFuture(null)
                : awaitFlush(insertPosition.updateAndGet(p -> p + values.length));
    }

    /**
     * Adds a collection of values to the backlog.
     *
     * @param values collection of values to enqueue
     * @return a future that completes when the values are processed by the consumer.
     */
    public CompletableFuture<Void> add(Collection<? extends T> values) {
        queue.addAll(values);
        return values.isEmpty() ? CompletableFuture.completedFuture(null)
                : awaitFlush(insertPosition.updateAndGet(p -> p + values.size()));
    }

    private CompletableFuture<Void> awaitFlush(long untilPosition) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        results.put(untilPosition, result);
        flushIfNotFlushing();
        return result;
    }

    private void flushIfNotFlushing() {
        if (flushing.compareAndSet(false, true)) {
            executorService.execute(this::flush);
        }
    }

    private void flush() {
        try {
            while (!queue.isEmpty()) {
                List<T> batch = new ArrayList<>(maxBatchSize);
                while (batch.size() < maxBatchSize) {
                    T value = queue.poll();
                    if (value == null) {
                        break;
                    }
                    batch.add(value);
                }
                CompletableFuture<?> future;
                try {
                    future = consumer.apply(batch);
                } catch (Throwable e) {
                    future = CompletableFuture.failedFuture(e);
                    errorHandler.handleError(e, batch);
                }
                long lastPosition = flushPosition.addAndGet(batch.size());
                if (future == null) {
                    completeResults(lastPosition, null);
                } else {
                    future.whenComplete((r, e) -> completeResults(lastPosition, e));
                }
                monitors.forEach(m -> m.accept(batch));
            }
            flushing.set(false);
            if (!queue.isEmpty()) { //a value could've been added after the while loop before flushing was set to false
                flushIfNotFlushing();
            }
        } catch (Throwable e) {
            log.error("Failed to flush the backlog", e);
            flushing.set(false);
            throw e;
        }
    }

    protected void completeResults(long untilPosition, Throwable e) {
        var futures = results.headMap(untilPosition, true);
        futures.forEach((k, v) -> {
            if (e == null) {
                v.complete(null);
            } else {
                v.completeExceptionally(e);
            }
        });
        futures.clear();
    }

    /**
     * Adds a monitor to observe flushed batches.
     *
     * @param monitor the observer
     * @return a {@link Registration} that can be used to remove the monitor
     */
    @Override
    public Registration registerMonitor(Consumer<List<T>> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }

    /**
     * Shuts down the internal executor service cleanly.
     */
    public void shutDown() {
        try {
            executorService.shutdown();
            try {
                executorService.awaitTermination(1L, SECONDS);
            } catch (InterruptedException e) {
                log.warn("Shutdown of executor was interrupted", e);
                Thread.currentThread().interrupt();
            }
        } catch (Throwable e) {
            log.warn("Failed to shutdown a backlog", e);
        }
    }

    /**
     * A function that consumes a batch of items and returns a future that completes when processing is done.
     */
    @FunctionalInterface
    public interface BatchConsumer<T> {
        CompletableFuture<Void> accept(List<T> batch) throws Exception;
    }
}
