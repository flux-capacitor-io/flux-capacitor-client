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
import lombok.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

@AllArgsConstructor
public class MemoizingFunction<K, V> implements Function<K, V> {
    private static final Entry nullValue = new Entry(null);
    private final ConcurrentHashMap<Object, Entry> map = new ConcurrentHashMap<>();
    private final Function<K, V> delegate;
    private final Duration lifespan;
    private final Supplier<Clock> clock;

    public MemoizingFunction(Function<K, V> delegate) {
        this(delegate, null, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V apply(K key) {
        Entry result = map.compute(
                Optional.<Object>ofNullable(key).orElse(nullValue),
                (k, v) -> v == null || (v.expiry != null && v.expiry.isBefore(clock.get().instant())) ? ofNullable(
                        delegate.apply(k == nullValue ? null : key))
                        .map(value -> new Entry(value,
                                                lifespan == null ? null :
                                                        clock.get().instant().plus(lifespan)))
                        .orElse(nullValue) : v);
        return (V) result.getValue();
    }

    public void clear() {
        map.clear();
    }

    @SuppressWarnings("unchecked")
    public V remove(K key) {
        return (V) Optional.ofNullable(map.remove(key)).map(Entry::getValue);
    }

    public boolean isCached(K key) {
        return key == null || map.containsKey(key);
    }

    @SuppressWarnings("unchecked")
    public void forEach(BiConsumer<? super K, ? super V> consumer) {
        map.forEach((k, e) -> consumer.accept((K) k, (V) e.value));
    }

    @Value
    @AllArgsConstructor
    static class Entry {

        Object value;
        Instant expiry;

        Entry(Object value) {
            this(value, null);
        }
    }
}
