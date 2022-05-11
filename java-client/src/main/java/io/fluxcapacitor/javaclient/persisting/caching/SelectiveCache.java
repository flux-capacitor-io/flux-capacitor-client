/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

@AllArgsConstructor
public class SelectiveCache implements Cache {
    public static Predicate<Object> aggregateSelector(Class<?> type) {
        return v -> {
            if (v instanceof AggregateRoot<?>) {
                AggregateRoot<?> aggregateRoot = (AggregateRoot<?>) v;
                return Optional.ofNullable(aggregateRoot.get()).map(r -> type.isAssignableFrom(r.getClass()))
                        .orElseGet(() -> type.isAssignableFrom(aggregateRoot.type()));
            }
            return false;
        };
    }

    private final Cache delegate;
    private final Predicate<Object> selector;
    private final Cache nextCache;

    public SelectiveCache(Cache nextCache, Predicate<Object> selector) {
        this(new DefaultCache(), selector, nextCache);
    }

    @Override
    public Object put(Object id, @NonNull Object value) {
        return selector.test(value) ? delegate.put(id, value) : nextCache.put(id, value);
    }

    @Override
    public Object putIfAbsent(Object id, @NonNull Object value) {
        return selector.test(value) ? delegate.putIfAbsent(id, value) : nextCache.putIfAbsent(id, value);
    }

    @Override
    public <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction) {
        T result = getIfPresent(id);
        return result == null ? selector.test(mappingFunction.apply(id))
                ? delegate.computeIfAbsent(id, mappingFunction) : nextCache.computeIfAbsent(id, mappingFunction)
                : result;
    }

    @Override
    public <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        T result = getIfPresent(id);
        return result == null ? null : selector.test(result) ? delegate.computeIfPresent(id, mappingFunction) :
                nextCache.computeIfPresent(id, mappingFunction);
    }

    @Override
    public <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction) {
        T result = Optional.<T>ofNullable(getIfPresent(id)).orElseGet(() -> mappingFunction.apply(id, null));
        return selector.test(result) ? delegate.compute(id, mappingFunction) : nextCache.compute(id, mappingFunction);
    }

    @Override
    public <T> T getIfPresent(Object id) {
        return Optional.<T>ofNullable(delegate.getIfPresent(id)).orElseGet(() -> nextCache.getIfPresent(id));
    }

    @Override
    public void invalidate(Object id) {
        delegate.invalidate(id);
        nextCache.invalidate(id);
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
        nextCache.invalidateAll();
    }

    @Override
    public int size() {
        return delegate.size() + nextCache.size();
    }
}
