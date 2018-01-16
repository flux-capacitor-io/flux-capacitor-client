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

package io.fluxcapacitor.javaclient.keyvalue;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Data;

/**
 * Represents a service to store and retrieve a piece of serialized data by key.
 */
public interface KeyValueClient {

    /**
     * Adds or replaces the given value in the key value store.
     *
     * @param key The key associated with this value
     * @param value The value to store
     * @return a handle that enables clients to wait until the value was safely sent to the store
     */
    Awaitable putValue(String key, Data<byte[]> value);

    /**
     * Returns the {@link Data} object associated with the given key. Returns {@code null} if there is no
     * associated value.
     *
     * @param key The key associated with the value
     * @return the value for the given key or null
     */
    Data<byte[]> getValue(String key);

    /**
     * Deletes the value associated with the given key.
     *
     * @param key The key associated with this value
     * @return a handle that enables clients to wait until the command was safely sent to the store
     */
    Awaitable deleteValue(String key);

}
