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

/**
 * Equivalent of {@link java.util.function.Function} whose {@link #apply(Object)} method may throw a checked
 * {@link Exception}.
 * <p>
 * This interface helps to avoid wrapping checked exceptions in runtime exceptions when using functional APIs.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
public interface ThrowingFunction<T, R> {
    /**
     * Applies this function to the given argument.
     *
     * @param t the function argument
     * @return the function result
     * @throws Exception if unable to compute a result
     */
    R apply(T t) throws Exception;

    /**
     * Returns a composed function that first applies the {@code before} function to its input, and then applies this
     * function to the result.
     *
     * @param before the function to apply before this function
     * @param <V>    the type of input to the {@code before} function, and to the composed function
     * @return a composed function that first applies the {@code before} function and then this function
     */
    default <V> ThrowingFunction<V, R> compose(@NonNull ThrowingFunction<? super V, ? extends T> before) {
        return v -> apply(before.apply(v));
    }

    /**
     * Returns a composed function that first applies this function to its input, and then applies the
     * {@code after} function to the result.
     *
     * @param after the function to apply after this function
     * @param <V>   the type of output of the {@code after} function, and of the composed function
     * @return a composed function that first applies this function and then the {@code after} function
     */
    default <V> ThrowingFunction<T, V> andThen(@NonNull ThrowingFunction<? super R, ? extends V> after) {
        return t -> after.apply(apply(t));
    }
}
