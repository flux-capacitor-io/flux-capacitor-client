/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.common.repository;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.ExecutionException;

public class CachingRepository<T> implements Repository<T> {

    private final Repository<T> delegate;
    private final Cache<Object, T> cache;

    public CachingRepository(Repository<T> delegate) {
        this(delegate, CacheBuilder.newBuilder().maximumSize(100_000).build());
    }

    public CachingRepository(Repository<T> delegate, Cache<Object, T> cache) {
        this.delegate = delegate;
        this.cache = cache;
    }

    @Override
    public void put(Object id, T value) {
        cache.put(id, value);
        delegate.put(id, value);
    }

    @Override
    public T get(Object id) {
        try {
            return cache.get(id, () -> delegate.get(id));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Delegate repository threw an exception while loading " + id, e);
        }
    }

    @Override
    public void delete(Object id) {
        cache.invalidate(id);
        delegate.delete(id);
    }
}
