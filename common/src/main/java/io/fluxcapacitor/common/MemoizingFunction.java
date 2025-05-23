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

public interface MemoizingFunction<K, V> extends Function<K, V> {

    void clear();

    V remove(K key);

    boolean isCached(K key);

    void forEach(Consumer<? super V> consumer);

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
