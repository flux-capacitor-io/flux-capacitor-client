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
 * A {@link PropertySource} implementation that reads configuration properties from the JVM system properties.
 * <p>
 * This source delegates to {@link System#getProperties()} to retrieve key-value pairs. It allows for application
 * configuration using the {@code -Dkey=value} syntax when starting the JVM.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * java -Dmy.config.value=42 -jar my-app.jar
 * }</pre>
 *
 * <p>This value could then be resolved via {@link PropertySource#get(String) PropertySource#get("my.config.value")}.
 *
 * @see JavaPropertiesSource
 * @see System#getProperties()
 */
public class SystemPropertiesSource extends JavaPropertiesSource {
    public SystemPropertiesSource() {
        super(System.getProperties());
    }
}
