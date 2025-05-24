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

import lombok.NonNull;

import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link BiFunction} that memoizes (caches) its results based on a pair of input arguments. The result is stored per
 * distinct (T, U) key pair and reused for identical subsequent calls.
 *
 * <p>Use {@code io.fluxcapacitor.javaclient.common.ClientUtils#memoize(BiFunction)} to create an instance.
 *
 * <pre>{@code
 * MemoizingBiFunction<String, Integer, String> repeater =
 *     ClientUtils.memoize((s, i) -> s.repeat(i));
 *
 * String result = repeater.apply("a", 3); // computes "aaa"
 * boolean cached = repeater.isCached("a", 3); // true
 * repeater.remove("a", 3); // evicts the cached result
 * }</pre>
 *
 * <p>Time-limited memoization is also supported:
 * <pre>{@code
 * MemoizingBiFunction<String, Locale, Translation> translator =
 *     ClientUtils.memoize(this::translate, Duration.ofMinutes(10));
 * }</pre>
 *
 * @param <T> the first input type
 * @param <U> the second input type
 * @param <R> the result type
 */
public interface MemoizingBiFunction<T, U, R> extends BiFunction<T, U, R> {

    /**
     * Returns {@code true} if the function has already cached a value for the given inputs.
     */
    boolean isCached(T t, U u);

    /**
     * Removes the cached result for the given input pair, if present.
     *
     * @return the previously cached result or {@code null}
     */
    R remove(T t, U u);

    /**
     * Clears all cached values.
     */
    void clear();

    /**
     * Applies the given consumer to all currently cached values.
     */
    void forEach(Consumer<? super R> consumer);

    /**
     * Creates a {@link MemoizingFunction} by composing this memoizing bi-function with an input transformation. This is
     * useful when you want to adapt this function to take a single composite key (like a {@link Entry}).
     *
     * @param before function that transforms the input into a {@link Entry pair} of arguments
     * @param <K1>   the type of the composed input
     * @return a composed {@link MemoizingFunction}
     */
    @NonNull
    default <K1> MemoizingFunction<K1, R> compose(
            @NonNull Function<? super K1, Entry<? extends T, ? extends U>> before) {
        MemoizingBiFunction<T, U, R> origin = this;

        return new MemoizingFunction<>() {
            @Override
            public R apply(K1 key) {
                Entry<? extends T, ? extends U> pair = before.apply(key);
                return origin.apply(pair.getKey(), pair.getValue());
            }

            @Override
            public R remove(K1 key) {
                Entry<? extends T, ? extends U> pair = before.apply(key);
                return origin.remove(pair.getKey(), pair.getValue());
            }

            @Override
            public boolean isCached(K1 key) {
                Entry<? extends T, ? extends U> pair = before.apply(key);
                return origin.isCached(pair.getKey(), pair.getValue());
            }

            @Override
            public void forEach(Consumer<? super R> consumer) {
                origin.forEach(consumer);
            }

            @Override
            public void clear() {
                origin.clear();
            }
        };
    }
}
