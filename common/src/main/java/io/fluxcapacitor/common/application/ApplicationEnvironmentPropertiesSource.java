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

import io.fluxcapacitor.common.FileUtils;

import java.util.Optional;
import java.util.Properties;

import static java.util.Optional.ofNullable;

/**
 * A {@link PropertySource} that loads environment-specific properties from an {@code application-<env>.properties} file
 * on the classpath.
 * <p>
 * This is commonly used for supporting configuration overrides based on the deployment environment (e.g. {@code dev},
 * {@code staging}, {@code production}).
 *
 * <p>The environment name is resolved from system environment variables or system properties, in the
 * following order of precedence:
 * <ol>
 *   <li>{@code ENVIRONMENT} environment variable</li>
 *   <li>{@code environment} environment variable</li>
 *   <li>{@code ENVIRONMENT} system property</li>
 *   <li>{@code environment} system property</li>
 * </ol>
 *
 * <p>For example, if the environment is resolved as {@code staging}, this class attempts to load and merge
 * all matching {@code application-staging.properties} files from the classpath. If no environment is specified,
 * this source remains empty and resolves no properties.
 *
 * <h2>Example:</h2>
 * <pre>
 * # application-staging.properties
 * flux.url=https://staging.api.example.com
 * flux.debug=true
 * </pre>
 *
 * <p>This class complements {@link ApplicationPropertiesSource} for layered configuration. Typically,
 * general values are defined in {@code application.properties}, while environment-specific values override them
 * via {@code application-<env>.properties}.
 *
 * <p>This class uses {@link FileUtils#loadProperties(String)} internally to support merging across modules,
 * and warns when duplicate keys occur across files.
 *
 * @see JavaPropertiesSource
 * @see ApplicationPropertiesSource
 */
public class ApplicationEnvironmentPropertiesSource extends JavaPropertiesSource {

    /**
     * Constructs an {@code ApplicationEnvironmentPropertiesSource} based on the resolved environment. If no environment
     * is configured, this source will be empty.
     */
    public ApplicationEnvironmentPropertiesSource() {
        this(getEnvironment());
    }

    /**
     * Constructs an {@code ApplicationEnvironmentPropertiesSource} using the specified environment name.
     *
     * @param environment the environment name (e.g., {@code dev}, {@code prod}); may be {@code null}.
     */
    public ApplicationEnvironmentPropertiesSource(String environment) {
        super(loadProperties(environment));
    }

    /**
     * Loads properties from an {@code application-<env>.properties} file if the environment name is present.
     *
     * @param environment the resolved environment
     * @return the loaded properties, or an empty set if no environment was specified
     */
    protected static Properties loadProperties(String environment) {
        return Optional.ofNullable(environment)
                .map(e -> String.format("application-%s.properties", e))
                .map(FileUtils::loadProperties)
                .orElseGet(Properties::new);
    }

    /**
     * Attempts to resolve the active environment using environment variables or system properties.
     *
     * @return the resolved environment name, or {@code null} if not set
     */
    protected static String getEnvironment() {
        return ofNullable(System.getenv("ENVIRONMENT"))
                .or(() -> ofNullable(System.getenv("environment")))
                .or(() -> ofNullable(System.getProperty("ENVIRONMENT")))
                .or(() -> ofNullable(System.getProperty("environment")))
                .orElse(null);
    }
}
