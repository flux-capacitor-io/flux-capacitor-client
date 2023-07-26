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
import java.util.function.BiFunction;
import java.util.function.Supplier;

@AllArgsConstructor
public class MemoizingBiFunction<T, U, R> implements BiFunction<T, U, R> {
    private final MemoizingFunction<Pair<T, U>, R> function;

    public MemoizingBiFunction(BiFunction<T, U, R> delegate) {
        this(delegate, null, null);
    }

    public MemoizingBiFunction(BiFunction<T, U, R> delegate, Duration lifespan, Supplier<Clock> clockSupplier) {
        this.function = new MemoizingFunction<>(p -> delegate.apply(p.getFirst(), p.getSecond()), lifespan, clockSupplier);
    }

    @Override
    public R apply(T t, U u) {
        return function.apply(new Pair<>(t, u));
    }

    public boolean isCached(T t, U u) {
        return function.isCached(new Pair<>(t, u));
    }

}
