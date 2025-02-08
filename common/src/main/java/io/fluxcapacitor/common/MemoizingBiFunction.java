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

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public interface MemoizingBiFunction<T, U, R> extends BiFunction<T, U, R> {
    boolean isCached(T t, U u);

    void clear();

    R remove(T t, U u);

    void forEach(Consumer<? super R> consumer);

    @NonNull
    default <K1> MemoizingFunction<K1, R> compose(@NonNull Function<? super K1, Pair<? extends T, ? extends U>> before) {
        MemoizingBiFunction<T, U, R> origin = this;

        return new MemoizingFunction<>() {
            @Override
            public R apply(K1 key) {
                Pair<? extends T, ? extends U> pair = before.apply(key);
                return origin.apply(pair.getFirst(), pair.getSecond());
            }

            @Override
            public R remove(K1 key) {
                Pair<? extends T, ? extends U> pair = before.apply(key);
                return origin.remove(pair.getFirst(), pair.getSecond());
            }

            @Override
            public boolean isCached(K1 key) {
                Pair<? extends T, ? extends U> pair = before.apply(key);
                return origin.isCached(pair.getFirst(), pair.getSecond());
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
