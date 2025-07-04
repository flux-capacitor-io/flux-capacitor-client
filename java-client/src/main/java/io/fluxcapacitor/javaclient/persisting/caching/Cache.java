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

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A generic cache interface for storing and managing objects identified by unique keys.
 * <p>
 * The {@code Cache} interface is primarily used by Flux Capacitor to store entities or other frequently accessed
 * objects.
 * <p>
 * The interface supports standard cache operations such as put, get, remove, compute, and clear, and also allows for
 * registering eviction listeners to track when items are removed.
 *
 * <p><strong>Note:</strong> All keys and values in this cache are treated as {@code Object}; callers are responsible
 * for type safety when retrieving values.
 */
public interface Cache {

    /**
     * Puts a value in the cache for the given {@code id}, overwriting any existing value.
     *
     * @param id    the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @return the previous value associated with the {@code id}, or {@code null} if none
     */
    Object put(Object id, Object value);

    /**
     * Associates the specified value with the given {@code id} only if no value is currently associated.
     *
     * @param id    the key to check for presence
     * @param value the value to associate if absent
     * @return the existing value associated with the key, or {@code null} if the new value was successfully put
     */
    Object putIfAbsent(Object id, Object value);

    /**
     * If a value is not already associated with the given {@code id}, computes and stores one using the given
     * function.
     *
     * @param id              the key to check or compute
     * @param mappingFunction the function to compute a value if absent
     * @param <T>             the expected type of the value
     * @return the current or newly computed value
     */
    <T> T computeIfAbsent(Object id, Function<? super Object, T> mappingFunction);

    /**
     * If a value is already associated with the given {@code id}, computes a new value using the provided function and
     * replaces the old one.
     *
     * @param id              the key to compute for
     * @param mappingFunction the function to compute a new value from the current one
     * @param <T>             the expected type of the value
     * @return the newly computed value, or {@code null} if the mapping function returned {@code null}
     */
    <T> T computeIfPresent(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction);

    /**
     * Computes and stores a new value for the given {@code id} using the provided function.
     * <p>
     * The previous value (if any) is provided to the function. The result is stored in the cache.
     *
     * @param id              the key to compute for
     * @param mappingFunction the function to compute a new value
     * @param <T>             the expected type of the value
     * @return the newly computed value, or {@code null} if the mapping function returned {@code null}
     */
    <T> T compute(Object id, BiFunction<? super Object, ? super T, ? extends T> mappingFunction);

    /**
     * Applies the given modifier function to all values currently in the cache.
     * <p>
     * This is useful for bulk modifications, e.g. adjusting internal state after a system-wide change.
     *
     * @param modifierFunction the function to apply to each entry
     * @param <T>              the expected type of the values
     */
    <T> void modifyEach(BiFunction<? super Object, ? super T, ? extends T> modifierFunction);

    /**
     * Retrieves the value associated with the given {@code id}, or {@code null} if not found.
     *
     * @param id  the key to retrieve
     * @param <T> the expected type of the value
     * @return the cached value, or {@code null} if absent
     */
    <T> T get(Object id);

    /**
     * Retrieves the value associated with the given {@code id}, or returns the specified default if not present.
     *
     * @param id           the key to retrieve
     * @param defaultValue the value to return if the key is not found
     * @param <T>          the expected type of the value
     * @return the cached value or the default
     */
    default <T> T getOrDefault(Object id, T defaultValue) {
        return Optional.<T>ofNullable(get(id)).orElse(defaultValue);
    }

    /**
     * Checks whether the cache contains an entry for the given {@code id}.
     *
     * @param id the key to check
     * @return {@code true} if the key exists in the cache, {@code false} otherwise
     */
    boolean containsKey(Object id);

    /**
     * Removes the entry associated with the given {@code id}, if present.
     *
     * @param id  the key to remove
     * @param <T> the expected type of the removed value
     * @return the removed value, or {@code null} if no value was associated with the key
     */
    <T> T remove(Object id);

    /**
     * Removes all entries from the cache.
     */
    void clear();

    /**
     * Returns the number of entries currently stored in the cache.
     *
     * @return the number of entries
     */
    int size();

    /**
     * Returns whether the cache is empty.
     *
     * @return {@code true} if the cache is empty; otherwise {@code false}
     */
    default boolean isEmpty() {
        return size() < 1;
    }

    /**
     * Registers a listener to be notified whenever a cache entry is evicted or removed.
     *
     * @param listener a function that consumes {@link CacheEviction}s
     * @return a registration that can be used to cancel the listener
     */
    Registration registerEvictionListener(Consumer<CacheEviction> listener);

    /**
     * Closes the cache and releases all associated resources.
     */
    void close();
}
