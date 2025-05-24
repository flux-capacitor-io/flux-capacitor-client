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
 * Variant of {@link java.util.function.BiFunction} whose {@link #apply(Object, Object)} method is allowed to
 * throw a checked {@link Exception}.
 * <p>
 * It can be used in place of a standard {@code BiFunction} when the operation may fail with a checked exception.
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <R> the type of the result of the function
 */
@FunctionalInterface
public interface ThrowingBiFunction<T, U, R> {

    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @return the function result
     * @throws Exception if unable to compute a result
     */
    R apply(T t, U u) throws Exception;

    /**
     * Returns a composed function that first applies this function to its input, and then applies the
     * {@code after} function to the result.
     *
     * @param after the function to apply after this function
     * @param <V>   the type of output of the {@code after} function, and of the composed function
     * @return a composed function that first applies this function and then the {@code after} function
     */
    default <V> ThrowingBiFunction<T, U, V> andThen(@NonNull ThrowingFunction<? super R, ? extends V> after) {
        return (T t, U u) -> after.apply(apply(t, u));
    }
}
