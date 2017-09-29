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
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class ObjectUtils {

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getValue(Map<?, ?> map, Object key) {
        return Optional.ofNullable((T) map.get(key));
    }

    @SuppressWarnings("unchecked")
    public static <T> T require(Map<?, ?> map, Object key) {
        return (T) requireNonNull(map.get(key), () -> format("Map %s does not have a mapping for key %s", map, key));
    }

    public static void ifTrue(boolean value, Runnable task) {
        if (value) {
            task.run();
        }
    }

    public static void ifFalse(boolean value, Runnable task) {
        if (!value) {
            task.run();
        }
    }

    @SuppressWarnings("unchecked")
    public static byte[] concat(byte[] array1, byte[] array2) {
        byte[] result = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    public static <T> Stream<T> iterate(T seed, UnaryOperator<T> f, Predicate<T> breakCondition) {
        return StreamSupport.stream(new BreakingSpliterator<>(Stream.iterate(seed, f), breakCondition), false);
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
