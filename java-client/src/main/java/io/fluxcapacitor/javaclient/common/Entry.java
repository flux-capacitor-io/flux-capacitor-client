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

/**
 * Represents a key-value pair with a string-based identifier.
 * <p>
 * This interface is commonly used in Flux to model lightweight references to domain objects, documents, or indexed
 * values. It is intentionally simple and generic to allow broad reuse across components such as the
 * {@code DocumentStore}, {@code HandlerRepository}, and other infrastructure abstractions.
 *
 * @param <T> the type of the associated value
 */
public interface Entry<T> {

    /**
     * Returns the identifier of the entry. Typically, this corresponds to an entity ID, document ID, or association
     * key.
     *
     * @return the unique ID as a {@code String}
     */
    String getId();

    /**
     * Returns the value associated with this entry. This may be a domain object, handler instance, or deserialized
     * document depending on usage.
     *
     * @return the value of type {@code T}
     */
    T getValue();
}
