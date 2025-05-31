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

package io.fluxcapacitor.javaclient.persisting.keyvalue;

import io.fluxcapacitor.common.Guarantee;

/**
 * A simple interface for storing, retrieving, and removing key-value pairs.
 * <p>
 * This interface provides basic persistence operations such as storing values (with optional guarantees),
 * retrieving them by key, conditionally storing only if absent, and deleting by key.
 *
 * <p><strong>Note:</strong> This API is considered legacy in the Flux Capacitor platform. It is recommended
 * to use the more advanced and flexible {@code DocumentStore} instead, which supports structured querying,
 * indexing, updates, and document lifecycle features.
 *
 * <p>This interface is still supported internally for backward compatibility and simple data use cases.
 *
 * @see io.fluxcapacitor.javaclient.persisting.search.DocumentStore
 */
public interface KeyValueStore {

    /**
     * Stores a value under the given key with the default {@link Guarantee#SENT} delivery guarantee.
     *
     * @param key   the key to store the value under
     * @param value the value to store
     */
    default void store(String key, Object value) {
        store(key, value, Guarantee.SENT);
    }

    /**
     * Stores a value under the given key with the specified delivery guarantee.
     *
     * @param key       the key to store the value under
     * @param value     the value to store
     * @param guarantee the delivery guarantee (e.g., SENT, STORED)
     */
    void store(String key, Object value, Guarantee guarantee);

    /**
     * Stores a value only if there is no existing value for the specified key.
     *
     * @param key   the key to store the value under
     * @param value the value to store
     * @return {@code true} if the value was stored, {@code false} if the key already had a value
     */
    boolean storeIfAbsent(String key, Object value);

    /**
     * Retrieves the value associated with the given key.
     *
     * @param key the key to retrieve
     * @param <R> the expected result type
     * @return the stored value, or {@code null} if not found
     */
    <R> R get(String key);

    /**
     * Removes the value associated with the given key.
     *
     * @param key the key to delete
     */
    void delete(String key);
}
