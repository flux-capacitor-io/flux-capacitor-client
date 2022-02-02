/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Spliterators;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.function.UnaryOperator.identity;

public class ObjectUtils {

    public static <T> Stream<T> iterate(T seed, UnaryOperator<T> f, Predicate<T> breakCondition) {
        return StreamSupport.stream(new BreakingSpliterator<>(Stream.iterate(seed, f), breakCondition), false);
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

    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier) {
        return new MemoizingSupplier<>(supplier);
    }

    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier) {
        return new MemoizingFunction<>(supplier);
    }

    public static <T, U, R> MemoizingBiFunction<T, U, R> memoize(BiFunction<T, U, R> supplier) {
        return new MemoizingBiFunction<>(supplier);
    }

    public static Consumer<Runnable> ifTrue(boolean check) {
        if (check) {
            return Runnable::run;
        }
        return r -> {};
    }

    @SneakyThrows
    public static Object forceThrow(Throwable error) {
        throw error;
    }

    @SneakyThrows
    public static <T> T safelyCall(Callable<T> callable) {
        return callable.call();
    }

    public static <T> Supplier<T> asSupplier(Callable<T> callable) {
        return () -> safelyCall(callable);
    }

    public static Runnable asRunnable(Callable<?> callable) {
        return () -> safelyCall(callable);
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

    public static class MemoizingSupplier<T> implements Supplier<T> {
        private final MemoizingFunction<Object, T> delegate;
        private final Object singleton = new Object();

        public MemoizingSupplier(Supplier<T> delegate) {
            this.delegate = new MemoizingFunction<>(o -> delegate.get());
        }

        @Override
        public T get() {
            return delegate.apply(singleton);
        }

        public boolean isCached() {
            return delegate.isCached(singleton);
        }
    }

    @AllArgsConstructor
    public static class MemoizingFunction<K, V> implements Function<K, V> {
        private final Map<K, V> map = new ConcurrentHashMap<>();
        private final Function<K, V> delegate;

        @Override
        public V apply(K key) {
            V v = map.get(key);
            if (v == null) {
                synchronized (delegate) {
                    v = map.get(key);
                    if (v == null) {
                        return map.computeIfAbsent(key, delegate);
                    }
                }
            }
            return v;
        }

        public boolean isCached(K key) {
            return map.containsKey(key);
        }
    }

    @AllArgsConstructor
    public static class MemoizingBiFunction<T, U, R> implements BiFunction<T, U, R> {
        private final Map<T, MemoizingFunction<U, R>> map = new ConcurrentHashMap<>();
        private final BiFunction<T, U, R> delegate;

        @Override
        public R apply(T t, U u) {
            MemoizingFunction<U, R> f = map.get(t);
            if (f == null) {
                synchronized (delegate) {
                    f = map.get(t);
                    if (f == null) {
                        f = map.computeIfAbsent(t, t2 -> new MemoizingFunction<>(u2 -> delegate.apply(t2, u2)));
                    }
                }
            }
            return f.apply(u);
        }

        public boolean isCached(T t, U u) {
            return map.containsKey(t) && map.get(t).isCached(u);
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
