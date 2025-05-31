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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.Entry;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for storing and retrieving {@code @Stateful} handler instances.
 * <p>
 * Handlers are typically indexed and matched based on their {@code @Association} fields and methods.
 * Implementations of this interface provide the backing logic to locate, store, and remove these handlers.
 * <p>
 * This interface is particularly useful in scenarios involving sagas, process managers, or other
 * stateful event-driven components where runtime state needs to be persisted and recalled based on
 * message correlation data.
 */
public interface HandlerRepository {

    /**
     * Finds all handler instances that match the given set of associations.
     * <p>
     * The association map typically represents values extracted from incoming messages
     * (e.g., correlation keys) and will be matched against fields or methods annotated with
     * {@code @Association} in registered handler instances.
     *
     * @param associations a map of association values, where the key is the value to match and the
     *                     value is the corresponding property name or path (e.g. {@code foo/bar/name}) in the handler
     * @return a collection of entries representing matching handler instances
     */
    Collection<? extends Entry<?>> findByAssociation(Map<Object, String> associations);

    /**
     * Retrieves all currently stored handler instances.
     *
     * @return a collection of all known handler entries
     */
    Collection<? extends Entry<?>> getAll();

    /**
     * Persists or updates the handler instance identified by the given ID.
     *
     * @param id    the unique identifier for the handler instance
     * @param value the handler instance to persist
     * @return a future that completes when the operation has finished
     */
    CompletableFuture<?> put(Object id, Object value);

    /**
     * Removes the handler instance identified by the given ID from the repository.
     *
     * @param id the identifier of the handler instance to delete
     * @return a future that completes when the deletion has finished
     */
    CompletableFuture<?> delete(Object id);
}
