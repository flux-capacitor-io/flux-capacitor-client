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

import lombok.SneakyThrows;

import java.util.Properties;

/**
 * Base class for {@link PropertySource} implementations backed by a {@link java.util.Properties} object.
 * <p>
 * This class provides a thin wrapper over a {@code Properties} instance and offers a consistent
 * way to retrieve configuration values using the {@link #get(String)} method.
 *
 * <p>It can be extended by concrete sources such as {@link SystemPropertiesSource} or custom property file loaders.
 *
 * @see java.util.Properties
 * @see PropertySource
 */
public abstract class JavaPropertiesSource implements PropertySource {

    private final Properties properties;

    /**
     * Creates a new property source backed by the given {@link Properties} instance.
     *
     * @param properties the {@code Properties} instance to use for lookups
     */
    @SneakyThrows
    public JavaPropertiesSource(Properties properties) {
        this.properties = properties;
    }

    /**
     * Retrieves the value for the given property name.
     *
     * @param name the property key
     * @return the property value, or {@code null} if not present
     */
    @Override
    public String get(String name) {
        return properties.getProperty(name);
    }
}
