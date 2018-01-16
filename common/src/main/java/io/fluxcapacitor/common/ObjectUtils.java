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

import java.util.Iterator;
import java.util.Map;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ObjectUtils {

    public static <T> Stream<T> iterate(T seed, UnaryOperator<T> f, Predicate<T> breakCondition) {
        return StreamSupport.stream(new BreakingSpliterator<>(Stream.iterate(seed, f), breakCondition), false);
    }

    public static <T> Supplier<T> memoize(Supplier<T> supplier) {
        AtomicReference<T> cache = new AtomicReference<>();
        return () -> {
            synchronized (cache) {
                return cache.updateAndGet(existing -> existing == null ? supplier.get() : existing);
            }
        };
    }

    public static <K, V> Function<K, V> memoize(Function<K, V> supplier) {
        Map<K, V> map = new ConcurrentHashMap<>();
        return key -> map.computeIfAbsent(key, supplier);
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
