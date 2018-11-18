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

import lombok.AllArgsConstructor;

import java.util.Iterator;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ObjectUtils {

    public static <T> Stream<T> iterate(T seed, UnaryOperator<T> f, Predicate<T> breakCondition) {
        return StreamSupport.stream(new BreakingSpliterator<>(Stream.iterate(seed, f), breakCondition), false);
    }

    public static <T> MemoizingSupplier<T> memoize(Supplier<T> supplier) {
        return new MemoizingSupplier<>(supplier);
    }

    public static <K, V> MemoizingFunction<K, V> memoize(Function<K, V> supplier) {
        return new MemoizingFunction<>(supplier);
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
            return map.computeIfAbsent(key, delegate);
        }
        
        public boolean isCached(K key) {
            return map.containsKey(key);
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
