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

/**
 * Represents a store of objects of type {@link T}. If a repository does not support modifications it is free to ignore
 * those operations.
 *
 * @param <T> the object type stored in the repository
 */
public interface Repository<T> {

    /**
     * Adds or replaces a value in the repository. May be ignored if this repository does not support modifications.
     *
     * @param id    The object id
     * @param value The value to store
     */
    void put(Object id, T value);

    /**
     * Returns the value associated with the given id. If there is no association, {@code null} is returned.
     *
     * @param id The object id
     * @return The value associated with given id
     */
    T get(Object id);

    /**
     * Deletes the value associated with the given id.
     *
     * @param id The object id
     */
    void delete(Object id);

}
