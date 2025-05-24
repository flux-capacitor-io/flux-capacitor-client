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
 * Functional counterpart to {@link java.util.function.Consumer} that allows the {@link #accept(Object)} method to
 * throw a checked {@link Exception}.
 * <p>
 * This is useful when working with streams or other functional APIs that need to propagate exceptions.
 *
 * @param <T> the type of the input to the operation
 */
@FunctionalInterface
public interface ThrowingConsumer<T> {
    /**
     * Performs this operation on the given argument.
     *
     * @param t the input argument
     * @throws Exception if unable to process the argument
     */
    void accept(T t) throws Exception;

    /**
     * Returns a composed consumer that performs, in sequence, this operation followed by the {@code after}
     * operation.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code ThrowingConsumer}
     */
    default ThrowingConsumer<T> andThen(@NonNull ThrowingConsumer<? super T> after) {
        return (T t) -> { accept(t); after.accept(t); };
    }
}
