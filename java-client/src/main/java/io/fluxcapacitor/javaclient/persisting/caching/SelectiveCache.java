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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

@AllArgsConstructor
public class SelectiveCache implements Cache {
    public static Predicate<Object> aggregateSelector(Class<?> type) {
        return v -> v instanceof AggregateRoot<?> && type.isAssignableFrom(((AggregateRoot<?>) v).type());
    }

    private final Cache delegate;
    private final Predicate<Object> selector;
    private final Cache nextCache;

    public SelectiveCache(Cache nextCache, Predicate<Object> selector) {
        this(new DefaultCache(), selector, nextCache);
    }

    @Override
    public void put(String id, Object value) {
        if (selector.test(value)) {
            delegate.put(id, value);
        } else {
            nextCache.put(id, value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String id, Function<? super String, T> mappingFunction) {
        Object result = getIfPresent(id);
        if (result == null) {
            put(id, result = mappingFunction.apply(id));
        }
        return (T) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getIfPresent(String id) {
        return (T) Optional
                .ofNullable(delegate.getIfPresent(id)).orElseGet(() -> nextCache.getIfPresent(id));
    }

    @Override
    public void invalidate(String id) {
        delegate.invalidate(id);
        nextCache.invalidate(id);
    }

    @Override
    public void invalidateAll() {
        delegate.invalidateAll();
        nextCache.invalidateAll();
    }
}
