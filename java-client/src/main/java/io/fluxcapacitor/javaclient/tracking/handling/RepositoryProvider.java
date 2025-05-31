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

package io.fluxcapacitor.javaclient.tracking.handling;

import java.util.Map;

/**
 * Provides access to a singleton-style repository for a given class type.
 * <p>
 * This interface is commonly used to retrieve long-lived, in-memory or cache-backed data structures that are
 * scoped to a {@link io.fluxcapacitor.javaclient.FluxCapacitor} instance. Repositories are typically used to
 * model shared application state, pre-loaded caches, or instance-specific registries.
 */
@FunctionalInterface
public interface RepositoryProvider {

    /**
     * Returns a repository (typically a shared, type-scoped map) for the given class type.
     * <p>
     * The returned {@code Map<Object, T>} is expected to be a singleton per type and reused across calls.
     *
     * @param repositoryClass the class representing the repository entry type
     * @param <T>             the type of objects stored in the repository
     * @return a shared map representing the repository for the given type
     */
    <T> Map<Object, T> getRepository(Class<T> repositoryClass);
}
