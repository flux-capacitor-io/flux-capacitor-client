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
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Optional.ofNullable;

/**
 * A default implementation of the {@link MemoizingFunction} interface that provides caching functionality for computed
 * function results. The results are cached by key, and can be optionally configured with a time-based expiration
 * policy.
 * <p>
 * This class uses a {@link ConcurrentHashMap} internally for thread-safe storage of cached entries.
 *
 * @param <K> the type of input keys
 * @param <V> the type of values produced by applying the delegate function
 */
@AllArgsConstructor
public class DefaultMemoizingFunction<K, V> implements MemoizingFunction<K, V> {
    private static final Entry nullValue = new Entry(null);
    private final ConcurrentHashMap<Object, Entry> map = new ConcurrentHashMap<>();
    private final Function<K, V> delegate;
    private final Duration lifespan;
    private final Clock clock;

    public DefaultMemoizingFunction(Function<K, V> delegate) {
        this(delegate, null, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V apply(K key) {
        Entry result = map.compute(
                Optional.<Object>ofNullable(key).orElse(nullValue),
                (k, v) -> v == null || (v.expiry != null && v.expiry.isBefore(clock.instant())) ? ofNullable(
                        delegate.apply(k == nullValue ? null : key))
                        .map(value -> new Entry(value,
                                                lifespan == null ? null :
                                                        clock.instant().plus(lifespan)))
                        .orElse(nullValue) : v);
        return (V) result.getValue();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(K key) {
        return (V) Optional.ofNullable(map.remove(key)).map(Entry::getValue);
    }

    @Override
    public boolean isCached(K key) {
        return key == null || map.containsKey(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void forEach(Consumer<? super V> consumer) {
        map.values().forEach(e -> consumer.accept((V) e.getValue()));
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
