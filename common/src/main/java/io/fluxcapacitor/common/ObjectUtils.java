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

@Slf4j
public class ObjectUtils {
    private static final Predicate<Object> noOpPredicate = v -> true;
    private static final BiPredicate<Object, Object> noOpBiPredicate = (a, b) -> true;

    @SuppressWarnings("unchecked")
    public static <T> Predicate<T> noOpPredicate() {
        return (Predicate<T>) noOpPredicate;
    }

    @SuppressWarnings("unchecked")
    public static <T, U> BiPredicate<T, U> noOpBiPredicate() {
        return (BiPredicate<T, U>) noOpBiPredicate;
    }

    public static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static <T> Stream<T> iterate(T seed, UnaryOperator<T> f, Predicate<T> breakCondition) {
        return StreamSupport.stream(new BreakingSpliterator<>(Stream.iterate(seed, f), breakCondition), false);
    }

    @SafeVarargs
    public static <T> Stream<T> concat(Stream<? extends T>... streams) {
        return stream(streams).flatMap(Function.identity());
    }

    public static <T> List<T> deduplicate(List<T> list) {
        return deduplicate(list, identity());
    }

    public static <T> List<T> deduplicate(List<T> list, Function<T, ?> idFunction) {
        return deduplicate(list, idFunction, false);
    }

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

    public static Stream<?> asStream(Object value) {
        return switch (value) {
            case null -> Stream.empty();
            case Collection<?> objects -> objects.stream();
            case Stream<?> stream -> stream;
            case Optional<?> o -> o.stream();
            default -> Stream.of(value);
        };
    }

    public static Consumer<Runnable> ifTrue(boolean check) {
        return check ? Runnable::run : (r -> {});
    }

    @SneakyThrows
    public static Object forceThrow(Throwable error) {
        throw error;
    }

    @SneakyThrows
    public static <T> T call(Callable<T> callable) {
        return callable.call();
    }

    @SneakyThrows
    public static void run(ThrowingRunnable runnable) {
        runnable.run();
    }

    public static Callable<?> asCallable(ThrowingRunnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    public static Callable<?> asCallable(Runnable runnable) {
        return () -> {
            runnable.run();
            return null;
        };
    }

    public static void tryRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("Task {} failed", task, e);
        }
    }

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

    public static byte[] getBytes(ByteBuffer buffer) {
        buffer = buffer.duplicate();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    public static Throwable unwrapException(Throwable e) {
        if (e == null) {
            return null;
        }
        if (e instanceof CompletionException || e instanceof ExecutionException) {
            return unwrapException(e.getCause());
        }
        return e;
    }

    @SneakyThrows
    public static Properties asProperties(String content) {
        Properties result = new Properties();
        result.load(new StringReader(content));
        return result;
    }

    public static Properties copyOf(Properties properties) {
        Properties result = new Properties();
        result.putAll(properties);
        return result;
    }

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

    public static String newThreadName(String prefix) {
        return prefix + "-" + threadNumber.getAndIncrement();
    }

    public static ThreadFactory newThreadFactory(String prefix) {
        return new PrefixedThreadFactory(prefix);
    }

    public static ExecutorService newNamedWorkStealingPool(int parallelism, String prefix) {
        return new ForkJoinPool(parallelism, pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName(prefix + "-pool-" + worker.getPoolIndex());
            return worker;
        }, null, true);
    }

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
