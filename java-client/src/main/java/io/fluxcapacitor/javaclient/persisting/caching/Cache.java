/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import lombok.NonNull;

import java.util.function.Function;

public interface Cache {

    /**
     * Adds or replaces a value in the cache. Values of {@code null} are not permitted.
     *
     * @param id    The object id
     * @param value The value to cache
     */
    void put(String id, @NonNull Object value);

    /**
     * Adds or replaces a value in the cache. Values of {@code null} are not permitted.
     *
     * @param id    The object id
     * @param value The value to cache
     */
    void putIfAbsent(String id, @NonNull Object value);

    /**
     * Returns the value associated with the given id. If there is no association, the mapping function is used to
     * calculate a value. This value will be stored in the cache unless it is {@code null} in which case the cache is
     * not updated.
     *
     * @param <T>             the type of object to return from the cache
     * @param id              The object id
     * @param mappingFunction The function to compute a value if the key is not mapped to a value in the cache
     * @return The value associated with given id
     */
    <T> T computeIfAbsent(String id, Function<? super Object, T> mappingFunction);

    /**
     * Returns the value associated with the given id if it exists in the cache. If there is no association, {@code
     * null} is returned.
     *
     * @param id  The object id
     * @param <T> the type of object to return from the cache
     * @return The value associated with given id
     */
    <T> T getIfPresent(String id);

    /**
     * Invalidates the cache entry mapped to given id.
     */
    void invalidate(String id);

    /**
     * Invalidates all cache entries.
     */
    void invalidateAll();

}
