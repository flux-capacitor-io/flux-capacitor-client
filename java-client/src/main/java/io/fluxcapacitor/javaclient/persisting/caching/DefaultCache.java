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

import com.google.common.cache.CacheBuilder;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
@AllArgsConstructor
public class DefaultCache implements Cache {


    private final ConcurrentMap<String, Object> cache;

    public DefaultCache() {
        this(1_000);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public DefaultCache(int maxSize) {
        this.cache = (ConcurrentMap) CacheBuilder.newBuilder().maximumSize(maxSize).build().asMap();
    }

    @Override
    public void put(String id, @NonNull Object value) {
        cache.put(id, value);
    }

    @Override
    public void putIfAbsent(String id, @NonNull Object value) {
        cache.putIfAbsent(id, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T computeIfAbsent(String id, Function<? super String, T> mappingFunction) {
        return (T) cache.computeIfAbsent(id, mappingFunction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T computeIfPresent(String id, BiFunction<? super String, ? super T, ? extends T> mappingFunction) {
        return (T) cache.computeIfPresent(id, (BiFunction<? super String, ? super Object, ?>) mappingFunction);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T compute(String id, BiFunction<? super String, ? super T, ? extends T> mappingFunction) {
        return (T) cache.compute(id, (BiFunction<? super String, ? super Object, ?>) mappingFunction);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getIfPresent(String id) {
        return (T) cache.get(id);
    }

    @Override
    public void invalidate(String id) {
        cache.remove(id);
    }

    @Override
    public void invalidateAll() {
        cache.clear();
    }
}
