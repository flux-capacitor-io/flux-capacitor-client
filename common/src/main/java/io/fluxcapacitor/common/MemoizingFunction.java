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

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link Function} that memoizes (caches) its results by key. Once a result is computed for a specific key,
 * it is stored and returned for all subsequent invocations with the same key until it is explicitly cleared or evicted.
 *
 * <p>Instances of this interface are typically created using static utility methods in
 * {@code io.fluxcapacitor.javaclient.common.ClientUtils}, e.g.:
 *
 * <pre>{@code
 * MemoizingFunction<String, Integer> lengthCache =
 *     ClientUtils.memoize(String::length);
 *
 * int len = lengthCache.apply("hello"); // computes and caches
 * boolean cached = lengthCache.isCached("hello"); // true
 * lengthCache.remove("hello"); // evicts the cached value
 * }</pre>
 *
 * <p>Memoization with a time-based expiration can also be created using an overload:
 * <pre>{@code
 * MemoizingFunction<String, Integer> expiringCache =
 *     ClientUtils.memoize(String::length, Duration.ofMinutes(5));
 * }</pre>
 *
 * @param <K> the type of input
 * @param <V> the type of output
 */
public interface MemoizingFunction<K, V> extends Function<K, V> {

    /**
     * Removes all cached values from this function.
     */
    void clear();

    /**
     * Removes and returns the cached result for the given key, if present.
     *
     * @param key the key whose cached result should be removed
     * @return the previously cached value, or {@code null} if not present
     */
    V remove(K key);

    /**
     * Checks if the given key has a cached value.
     *
     * @param key the key to test
     * @return {@code true} if the value is cached, otherwise {@code false}
     */
    boolean isCached(K key);

    /**
     * Applies the given consumer to all currently cached values.
     *
     * @param consumer a consumer to operate on cached values
     */
    void forEach(Consumer<? super V> consumer);

    /**
     * Composes this memoizing function with another function. The resulting function first applies the given
     * {@code before} function to the input and then applies this memoizing function to the result.
     * <p>
     * The composed function retains memoization behavior for the transformed keys.
     *
     * @param <K1> the input type of the composed function
     * @param before a function to transform input before applying this function
     * @return a composed memoizing function
     */
    @Override
    @NonNull
    default <K1> MemoizingFunction<K1, V> compose(@NonNull Function<? super K1, ? extends K> before) {
        MemoizingFunction<K, V> origin = this;

        return new MemoizingFunction<>() {
            @Override
            public V apply(K1 key) {
                return origin.apply(before.apply(key));
            }

            @Override
            public V remove(K1 key) {
                return origin.remove(before.apply(key));
            }

            @Override
            public boolean isCached(K1 key) {
                return origin.isCached(before.apply(key));
            }

            @Override
            public void forEach(Consumer<? super V> consumer) {
                origin.forEach(consumer);
            }

            @Override
            public void clear() {
                origin.clear();
            }
        };
    }
}
