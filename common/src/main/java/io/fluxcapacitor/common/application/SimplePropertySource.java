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

package io.fluxcapacitor.common.application;

import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * A simple in-memory implementation of the {@link PropertySource} interface backed by a {@link Map}.
 *
 * <p>This class is typically used for programmatic configuration, testing, or cases where property values
 * are supplied dynamically at runtime from an explicit key-value source.
 *
 * <p>Example usage:
 * <pre>{@code
 * Map<String, String> props = Map.of("api.key", "12345", "timeout", "1000");
 * PropertySource propertySource = new SimplePropertySource(props);
 * String apiKey = propertySource.get("api.key");
 * }</pre>
 *
 * @see PropertySource
 * @see DefaultPropertySource
 */
@AllArgsConstructor
public class SimplePropertySource implements PropertySource {
    private final Map<String, String> properties;

    /**
     * Retrieves the value associated with the given property name from the internal map.
     *
     * @param name the name of the property to retrieve
     * @return the corresponding property value, or {@code null} if not present
     */
    @Override
    public String get(String name) {
        return properties.get(name);
    }
}
