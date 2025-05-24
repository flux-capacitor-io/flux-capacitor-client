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

import static io.fluxcapacitor.common.FileUtils.loadProperties;

/**
 * A {@link PropertySource} implementation that loads properties from an {@code application.properties} file located on
 * the classpath.
 * <p>
 * This is a conventional source for static configuration values such as credentials, service endpoints, or application
 * settings. The file is expected to be in the root of the classpath (e.g., inside {@code resources/}).
 *
 * <h2>Example {@code application.properties}:</h2>
 * <pre>
 * service.url=https://api.example.com
 * retry.count=3
 * </pre>
 *
 * <p>The loaded properties are backed by a {@link java.util.Properties} instance and exposed via the
 * {@link #get(String)} method.
 *
 * <p>This class uses {@link FileUtils#loadProperties(String)} which supports merging multiple files with the
 * same name from the classpath and logs duplicate key warnings.
 *
 * @see JavaPropertiesSource
 * @see FileUtils#loadProperties(String)
 */
public class ApplicationPropertiesSource extends JavaPropertiesSource {

    /**
     * Constructs a new {@code ApplicationPropertiesSource} by loading the {@code application.properties} file from the
     * classpath.
     */
    public ApplicationPropertiesSource() {
        super(loadProperties("application.properties"));
    }
}
