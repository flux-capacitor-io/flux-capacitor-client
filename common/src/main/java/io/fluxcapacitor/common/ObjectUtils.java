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

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.stream;
import static java.util.function.UnaryOperator.identity;

/**
 * Utility class for common object handling, memoization, concurrency, stream processing, and error handling.
 * <p>
 * Offers reusable tools for:
 * <ul>
 *   <li>Memoizing functional interfaces</li>
 *   <li>Retry-safe functional wrappers (callables, consumers, etc.)</li>
 *   <li>Stream deduplication and flattening</li>
 *   <li>Property file manipulation</li>
 *   <li>Exception-safe task execution and error unwrapping</li>
 *   <li>Custom thread factories and naming</li>
 * </ul>
 */
@Slf4j
public class ObjectUtils {
    private static final Predicate<Object> noOpPredicate = v -> true;
    private static final BiPredicate<Object, Object> noOpBiPredicate = (a, b) -> true;

    /**
     * Returns a predicate that always evaluates to true.
     */
    @SuppressWarnings("unchecked")
    public static <T> Predicate<T> noOpPredicate() {
        return (Predicate<T>) noOpPredicate;
    }

    /**
     * Returns a bi-predicate that always evaluates to true.
     */
    @SuppressWarnings("unchecked")
    public static <T, U> BiPredicate<T, U> noOpBiPredicate() {
        return (BiPredicate<T, U>) noOpBiPredicate;
    }

    /**
     * Returns a predicate that filters out duplicates based on a key extractor.
     */
    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    /**
     * Creates a stream that stops once the break condition evaluates true.
     */
    public static <T> Stream<T> iterate(T seed, UnaryOperator<T> f, Predicate<T> breakCondition) {
        return StreamSupport.stream(new BreakingSpliterator<>(Stream.iterate(seed, f), breakCondition), false);
    }

    /**
     * Concatenates multiple streams into a single flat stream.
     */
    @SafeVarargs
    public static <T> Stream<T> concat(Stream<? extends T>... streams) {
        return stream(streams).flatMap(Function.identity());
    }

    /**
     * Deduplicates elements in the list, preserving the last occurrence.
     */
    public static <T> List<T> deduplicate(List<T> list) {
        return deduplicate(list, identity());
    }

    /**
     * Deduplicates elements using a key extractor, preserving the last occurrence.
     */
    public static <T> List<T> deduplicate(List<T> list, Function<T, ?> idFunction) {
        return deduplicate(list, idFunction, false);
    }

    /**
     * Deduplicates elements using a key extractor, optionally keeping the first occurrence.
     */
    public static <T> List<T> deduplicate(List<T> list, Function<T, ?> idFunction, boolean keepFirst) {
        list = new ArrayList<>(list);
        Set<Object> ids = new HashSet<>();
        if (keepFirst) {
            list.removeIf(t -> !ids.add(idFunction.apply(t)));
        } else {
            ListIterator<T> iterator = list.listIterator(list.size());
            while (iterator.hasPrevious()) {
                if (!ids.add(idFunction.apply(iterator.previous()))) {
                    iterator.remove();
                }
            }
        }
        return list;
    }

    /**
     * Converts an object into a stream. Supports Collection, Stream, Optional, or single value.
     */
    public static Stream<?> asStream(Object value) {
        return switch (value) {
            case null -> Stream.empty();
            case Collection<?> objects -> objects.stream();
            case Stream<?> stream -> stream;
            case Optional<?> o -> o.stream();
            default -> Stream.of(value);
        };
    }

    /**
     * Returns a consumer that runs the task only if {@code check} is true.
     */
    public static Consumer<Runnable> ifTrue(boolean check) {
        return check ? Runnable::run : (r -> {});
    }

    /**
     * Forces the given throwable to be thrown.
     */
    @SneakyThrows
    public static Object forceThrow(Throwable error) {
        throw error;
    }

    /**
     * Calls the given callable, forcibly rethrowing exceptions.
     */
    @SneakyThrows
    public static <T> T call(Callable<T> callable) {
        return callable.call();
    }

    /**
     * Executes the runnable, forcibly rethrowing exceptions as unchecked.
     */
    @SneakyThrows
    public static void run(ThrowingRunnable runnable) {
        runnable.run();
    }

    /**
     * Converts a ThrowingRunnable into a {@code Callable<Object>} that returns null.
     */
    public static Callable<?> asCallable(ThrowingRunnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    /**
     * Converts a standard {@link Runnable} into a {@code Callable<Object>} that returns null.
     */
    public static Callable<?> asCallable(Runnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    /**
     * Executes the runnable and logs any thrown exception.
     */
    public static void tryRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("Task {} failed", task, e);
        }
    }

    /**
     * Wraps a runnable in a try/catch block that logs any exception on execution.
     */
    public static Runnable tryCatch(Runnable runnable) {
        return () -> tryRun(runnable);
    }

    public static <T> Supplier<T> asSupplier(Callable<T> callable) {
        return () -> call(callable);
    }

    public static Runnable asRunnable(ThrowingRunnable runnable) {
        return () -> run(runnable);
    }

    public static <T, R> Function<T, R> asFunction(ThrowingFunction<T, R> function) {
        return t -> call(() -> function.apply(t));
    }

    public static <T> Consumer<T> asConsumer(ThrowingConsumer<T> consumer) {
        return t -> run(() -> consumer.accept(t));
    }

    public static Runnable asRunnable(Callable<?> callable) {
        return () -> call(callable);
    }

    /**
     * Extracts the byte array from a {@link ByteBuffer}.
     */
    public static byte[] getBytes(ByteBuffer buffer) {
        buffer = buffer.duplicate();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    /**
     * Recursively unwraps the cause of common wrapping exceptions.
     */
    public static Throwable unwrapException(Throwable e) {
        if (e == null) {
            return null;
        }
        if (e instanceof CompletionException || e instanceof ExecutionException) {
            return unwrapException(e.getCause());
        }
        return e;
    }

    /**
     * Parses Java {@link Properties} from a string.
     */
    @SneakyThrows
    public static Properties asProperties(String content) {
        Properties result = new Properties();
        result.load(new StringReader(content));
        return result;
    }

    /**
     * Creates a deep copy of a Properties instance.
     */
    public static Properties copyOf(Properties properties) {
        Properties result = new Properties();
        result.putAll(properties);
        return result;
    }

    /**
     * Merges two Properties instances into a new one.
     */
    public static Properties merge(Properties a, Properties b) {
        Properties result = copyOf(a);
        result.putAll(b);
        return result;
    }

    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier) {
        return supplier instanceof MemoizingSupplier<T> existing ? existing : new DefaultMemoizingSupplier<>(supplier);
    }

    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier) {
        return supplier instanceof MemoizingFunction<K, V> existing ? existing : new DefaultMemoizingFunction<>(supplier);
    }

    public static <T, U, R> MemoizingBiFunction<T, U, R> memoize(BiFunction<T, U, R> supplier) {
        return supplier instanceof MemoizingBiFunction<T, U, R> existing ? existing : new DefaultMemoizingBiFunction<>(supplier);
    }

    private static final AtomicInteger threadNumber = new AtomicInteger(1);

    /**
     * Generates a unique thread name with the given prefix.
     */
    public static String newThreadName(String prefix) {
        return prefix + "-" + threadNumber.getAndIncrement();
    }

    /**
     * Creates a new {@link ThreadFactory} with a named prefix.
     */
    public static ThreadFactory newThreadFactory(String prefix) {
        return new PrefixedThreadFactory(prefix);
    }

    /**
     * Creates a named {@link ForkJoinPool} with the given parallelism.
     */
    public static ExecutorService newNamedWorkStealingPool(int parallelism, String prefix) {
        return new ForkJoinPool(parallelism, pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName(prefix + "-pool-" + worker.getPoolIndex());
            return worker;
        }, null, true);
    }

    /**
     * Returns a consumer that logs errors instead of propagating them.
     */
    public static <T> Consumer<? super T> tryAccept(Consumer<? super T> consumer) {
        return t -> tryRun(() -> consumer.accept(t));
    }

    private static class PrefixedThreadFactory implements ThreadFactory {
        private static final Map<String, AtomicInteger> poolCount = new ConcurrentHashMap<>();
        private final ThreadGroup group = Thread.currentThread().getThreadGroup();
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        public PrefixedThreadFactory(String poolPrefix) {
            namePrefix = poolPrefix + "-pool-" + poolCount.computeIfAbsent(poolPrefix, k -> new AtomicInteger(1)).getAndIncrement() + "-thread-";
        }

        @SuppressWarnings("NullableProblems")
        public Thread newThread(Runnable task) {
            Thread t = new Thread(group, task, namePrefix + threadNumber.getAndIncrement(), 0);
            if (t.isDaemon()) {
                t.setDaemon(false);
            }
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    private static class BreakingSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

        private final Iterator<T> delegate;
        private final Predicate<T> breakCondition;
        private boolean stopped;

        private BreakingSpliterator(Stream<T> delegate, Predicate<T> breakCondition) {
            super(Long.MAX_VALUE, 0);
            this.delegate = delegate.iterator();
            this.breakCondition = breakCondition;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (stopped) {
                return false;
            }
            T value = delegate.next();
            if (breakCondition.test(value)) {
                stopped = true;
            }
            action.accept(value);
            return true;
        }
    }

}
