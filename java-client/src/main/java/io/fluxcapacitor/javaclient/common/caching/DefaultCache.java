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


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.AllArgsConstructor;

import java.util.function.Function;

@AllArgsConstructor
public class DefaultCache implements io.fluxcapacitor.javaclient.common.caching.Cache {

    private final Cache<String, Object> cache;

    public DefaultCache() {
        this(Caffeine.newBuilder().maximumSize(1_000).build());
    }

    @Override
    public void put(String id, Object value) {
        cache.put(id, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(String id, Function<? super String, T> mappingFunction) {
        return (T) cache.get(id, mappingFunction);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
