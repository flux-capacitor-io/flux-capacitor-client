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

import lombok.AllArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A default implementation of the {@link MemoizingBiFunction} interface that memoizes (caches) results of a
 * {@link BiFunction} based on a pair of input arguments. Cached results can be reused for identical subsequent
 * calls, and the cache can optionally be configured with a lifespan and a custom clock for time-based eviction.
 *
 * This class internally uses a {@link MemoizingFunction}, with the input arguments wrapped as
 * {@link Map.Entry} for caching purposes.
 *
 * @param <T> the type of the first input argument
 * @param <U> the type of the second input argument
 * @param <R> the type of the computed result
 */
@AllArgsConstructor
public class DefaultMemoizingBiFunction<T, U, R> implements MemoizingBiFunction<T, U, R> {
    private final MemoizingFunction<Map.Entry<T, U>, R> function;

    public DefaultMemoizingBiFunction(BiFunction<T, U, R> delegate) {
        this(delegate, null, null);
    }

    public DefaultMemoizingBiFunction(BiFunction<T, U, R> delegate, Duration lifespan, Clock clock) {
        this.function = new DefaultMemoizingFunction<>(p -> delegate.apply(p.getKey(), p.getValue()), lifespan, clock);
    }

    @Override
    public R apply(T t, U u) {
        return function.apply(new SimpleEntry<>(t, u));
    }

    @Override
    public boolean isCached(T t, U u) {
        return function.isCached(new SimpleEntry<>(t, u));
    }

    @Override
    public void clear() {
        function.clear();
    }

    @Override
    public R remove(T t, U u) {
        return function.remove(new SimpleEntry<>(t, u));
    }

    @Override
    public void forEach(Consumer<? super R> consumer) {
        function.forEach(consumer);
    }

}
