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

package io.fluxcapacitor.javaclient.common.caching;

import lombok.SneakyThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class DefaultCache implements io.fluxcapacitor.javaclient.common.caching.Cache {

    private final LinkedHashMap<String, Object> cache;

    public DefaultCache() {
        this(1_000);
    }
    
    public DefaultCache(int maxSize) {
        cache = new LinkedHashMap<String, Object>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > maxSize;
            }
        };
    }

    @Override
    public void put(String id, Object value) {
        cache.put(id, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    @SneakyThrows
    public <T> T get(String id, Function<? super String, T> mappingFunction) {
        return (T) cache.computeIfAbsent(id, mappingFunction);
    }

    @Override
    @SuppressWarnings("unchecked")
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
