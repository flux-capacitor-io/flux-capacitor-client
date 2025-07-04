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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.modeling.Entity;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A {@link Cache} implementation that partitions values across two internal caches based on a provided
 * {@link Predicate}.
 * <p>
 * If a value matches the {@code selector}, it is stored in the delegate cache; otherwise, it is stored in the
 * {@code nextCache}. This is particularly useful when certain types of objects (e.g., aggregates, projections) require
 * specialized caching behavior while others do not.
 *
 * <p><strong>Chaining:</strong> {@code SelectiveCache} instances can be chained by using another
 * {@code SelectiveCache} as the {@code nextCache}. This enables complex multi-level cache routing logic, where each
 * level applies a different selector to direct objects to a different backing cache.
 *
 * <p><strong>Example use case:</strong> Route aggregates to one cache and all other entities to a fallback cache:
 * <pre>{@code
 * Predicate<Object> aggregateSelector = SelectiveCache.aggregateSelector(MyAggregate.class);
 * Cache aggregateCache = new DefaultCache();
 * Cache fallbackCache = new DefaultCache();
 * Cache selectiveCache = new SelectiveCache(aggregateCache, aggregateSelector, fallbackCache);
 * }</pre>
 */
@AllArgsConstructor
public class SelectiveCache implements Cache {
    /**
     * Utility predicate to match aggregates of a given root type. This checks the type of the value inside an
     * {@link Entity}, or the declared type if the value is null.
     */
    public static Predicate<Object> aggregateSelector(Class<?> type) {
        return v -> {
            if (v instanceof Entity<?> aggregateRoot) {
                return Optional.ofNullable(aggregateRoot.get()).map(r -> type.isAssignableFrom(r.getClass()))
                        .orElseGet(() -> type.isAssignableFrom(aggregateRoot.type()));
            }
            return false;
        };
    }

    private final Cache delegate;
    private final Predicate<Object> selector;
    private final Cache nextCache;

    /**
     * Constructs a {@code SelectiveCache} with a default in-memory delegate cache.
     *
     * @param nextCache The fallback cache used when {@code selector} does not match a value
     * @param selector  A predicate to decide which cache a value should go into
     */
    public SelectiveCache(Cache nextCache, Predicate<Object> selector) {
        this(new DefaultCache(), selector, nextCache);
    }

    @Override
    public Object put(Object id, Object value) {
        if (selector.test(value)) {
            Object delegateValue = delegate.put(id, value);
            Object nextCacheValue = nextCache.remove(id);
            return delegateValue == null ? nextCacheValue : delegateValue;
        }
        Object nextCacheValue = nextCache.put(id, value);
        Object delegateValue = delegate.remove(id);
        return nextCacheValue == null ? delegateValue : nextCacheValue;
    }

    @Override
    public Object putIfAbsent(Object id, Object value) {
        if (selector.test(value)) {
            Object delegateValue = delegate.putIfAbsent(id, value);
            Object nextCacheValue = nextCache.remove(id);
            return delegateValue == null ? nextCacheValue : delegateValue;
        }
        Object nextCacheValue = nextCache.putIfAbsent(id, value);
        Object delegateValue = delegate.remove(id);
        return nextCacheValue == null ? delegateValue : nextCacheValue;
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        return compute(id, (k, v) -> v == null ? mappingFunction.apply(id) : v);
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        return compute(id, (k, v) -> v == null ? null : mappingFunction.apply(k, v));
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        AtomicReference<T> result = new AtomicReference<>();
        T nextCacheResult = nextCache.compute(id, (k, v) -> {
            T delegateResult = delegate.compute(id, (k2, v2) -> {
                T r = mappingFunction.apply(id, v2 == null ? v : v2);
                result.set(r);
                if (selector.test(r)) {
                    return r;
                }
                return null;
            });
            if (selector.test(delegateResult)) {
                result.set(delegateResult);
                return null;
            }
            return result.get();
        });
        return nextCacheResult == null ? result.get() : nextCacheResult;
    }

    @Override
    public <T> T get(Object id) {
        return Optional.<T>ofNullable(delegate.get(id)).orElseGet(() -> nextCache.get(id));
    }

    @Override
    public boolean containsKey(Object id) {
        return delegate.containsKey(id) || nextCache.containsKey(id);
    }

    @Override
    public <T> T remove(Object id) {
        T delegateValue = delegate.remove(id);
        T nextCacheValue = nextCache.remove(id);
        return delegateValue == null ? nextCacheValue : delegateValue;
    }

    @Override
    public void clear() {
        delegate.clear();
        nextCache.clear();
    }

    @Override
    public <T> void modifyEach(BiFunction<? super Object, ? super T, ? extends T> modifierFunction) {
        delegate.modifyEach(modifierFunction);
        nextCache.modifyEach(modifierFunction);
    }

    @Override
    public int size() {
        return delegate.size() + nextCache.size();
    }

    @Override
    public Registration registerEvictionListener(Consumer<CacheEviction> listener) {
        return delegate.registerEvictionListener(listener).merge(nextCache.registerEvictionListener(listener));
    }

    @Override
    public void close() {
        delegate.close();
        nextCache.close();
    }
}
