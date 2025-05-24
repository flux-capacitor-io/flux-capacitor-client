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

package io.fluxcapacitor.common.api;

/**
 * Interface for exposing a string-based representation of an object's identifier.
 * <p>
 * Implementing {@code HasId} enables objects with strongly typed IDs—such as {@code UserId}, {@code ProjectId}, or
 * value objects—to participate in systems that require plain string identifiers, such as serialization, logging, or
 * infrastructure-level APIs.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Allows conversion of strongly typed IDs to a standardized {@code String} format</li>
 *   <li>Used in infrastructure components like {@link io.fluxcapacitor.common.api.search.constraints.MatchConstraint} or
 *       document indexing where a plain string ID is required</li>
 *   <li>Improves type-safety in domain models while maintaining compatibility with external systems</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * @Value
 * public class UserId implements HasId {
 *     UUID value;
 *
 *     @Override
 *     public String getId() {
 *         return value.toString();
 *     }
 * }
 * }</pre>
 */
public interface HasId {

    /**
     * Returns the stringified identifier of this object.
     * <p>
     * This is typically used for serialization or ID-based lookups.
     *
     * @return a non-null, plain string representation of the identifier
     */
    String getId();
}
