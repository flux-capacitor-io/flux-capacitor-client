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

package io.fluxcapacitor.common.serialization;

import java.util.Optional;

/**
 * Interface for resolving registered types by alias or simplified name.
 * <p>
 * A {@code TypeRegistry} is used to look up the fully qualified class name associated with a given alias.
 * Aliases may be:
 * <ul>
 *   <li>Simple class names (e.g., {@code "ProjectCreated"})</li>
 *   <li>Shortened fully qualified names (e.g., {@code "project.ProjectCreated"})</li>
 * </ul>
 * <p>
 * This mechanism is commonly used in deserialization of message payloads or documents, where the
 * {@code @class} property in a JSON object indicates the type name.
 *
 * <h2>Example</h2>
 * If a class {@code com.example.project.ProjectCreated} is registered using {@link RegisterType}, the registry may resolve:
 * <pre>{@code
 * getTypeName("ProjectCreated") -> "com.example.project.ProjectCreated"
 * getTypeName("project.ProjectCreated") -> "com.example.project.ProjectCreated"
 * }</pre>
 *
 * @see RegisterType
 */
public interface TypeRegistry {

    /**
     * Returns the fully qualified class name associated with the given alias or type key.
     *
     * @param alias the name or alias to resolve, typically obtained from {@code @class} in JSON
     * @return an {@code Optional} containing the resolved fully qualified class name, or empty if not found
     */
    Optional<String> getTypeName(String alias);
}
