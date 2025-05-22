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

package io.fluxcapacitor.javaclient.common;

import java.util.Iterator;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Strategy interface for generating unique identifiers used throughout the Flux platform.
 * <p>
 * An {@code IdentityProvider} produces identifiers for messages, schedules, user sessions,
 * documents, and other entities that require uniqueness.
 * </p>
 *
 * <p>
 * The interface defines two types of identifiers:
 * </p>
 * <ul>
 *   <li>{@link #nextFunctionalId()} – the main ID used in application-level constructs</li>
 *   <li>{@link #nextTechnicalId()} – defaults to {@code nextFunctionalId()}, but may be overridden for traceability</li>
 * </ul>
 *
 * <p>
 * Implementations can be discovered automatically using {@link java.util.ServiceLoader}.
 * If none are found, {@link UuidFactory} is used by default.
 * </p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * String id = IdentityProvider.defaultIdentityProvider.nextFunctionalId();
 * }</pre>
 *
 * @see UuidFactory
 */
@FunctionalInterface
public interface IdentityProvider {

    /**
     * The default identity provider, resolved using {@link ServiceLoader}, or falling back to {@link UuidFactory}.
     */
    IdentityProvider defaultIdentityProvider = Optional.of(ServiceLoader.load(IdentityProvider.class))
            .map(ServiceLoader::iterator)
            .filter(Iterator::hasNext)
            .map(Iterator::next)
            .orElseGet(UuidFactory::new);

    /**
     * Returns a new functional ID, suitable for user-visible or application-level tracking.
     *
     * @return a unique, non-null identifier string
     */
    String nextFunctionalId();

    /**
     * Returns a new technical ID, suitable for lower-level operations such as internal tracing.
     * <p>
     * Defaults to {@link #nextFunctionalId()}, but can be overridden.
     *
     * @return a unique identifier string
     */
    default String nextTechnicalId() {
        return nextFunctionalId();
    }
}
