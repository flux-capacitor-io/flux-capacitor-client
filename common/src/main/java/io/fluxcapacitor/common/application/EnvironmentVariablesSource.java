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

/**
 * A {@link PropertySource} that resolves property values from system environment variables.
 * <p>
 * This source accesses environment variables via {@link System#getenv(String)} and can be used to inject configuration
 * values directly from the host operating system or container runtime.
 *
 * <p>This is typically used to override configuration in deployment environments without modifying
 * application-specific property files.
 *
 * <h2>Example usage</h2>
 * <pre>
 * export FLUX_API_TOKEN=secret-token
 * </pre>
 * In your application:
 * <pre>
 * flux.api.token = ApplicationProperties.get("FLUX_API_TOKEN")
 * </pre>
 *
 * <p>This source is usually combined with others (like {@link ApplicationPropertiesSource}) in a layered
 * configuration strategy where environment variables take precedence.
 *
 * @see System#getenv(String)
 * @see ApplicationPropertiesSource
 * @see JavaPropertiesSource
 */
public enum EnvironmentVariablesSource implements PropertySource {
    INSTANCE;

    /**
     * Retrieves the value of the given property name from the system environment.
     *
     * @param name the name of the environment variable
     * @return the value, or {@code null} if the variable is not defined
     */
    @Override
    public String get(String name) {
        return System.getenv(name);
    }
}
