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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * A layered {@link PropertySource} implementation used as the default source for resolving application properties in a
 * Flux application.
 *
 * <p>This source provides a prioritized resolution order by chaining multiple individual sources:
 * <ol>
 *   <li>{@link EnvironmentVariablesSource} – highest precedence</li>
 *   <li>{@link SystemPropertiesSource}</li>
 *   <li>{@link ApplicationEnvironmentPropertiesSource} – e.g. application-dev.properties</li>
 *   <li>{@link ApplicationPropertiesSource} – fallback base configuration</li>
 * </ol>
 *
 * <p>Property values are resolved from the first source that defines them in this sequence.
 *
 * <p>This is used as the default property source in the {@code FluxCapacitor} client if no custom source is provided.
 */
@Slf4j
public class DefaultPropertySource implements PropertySource {
    @Getter(lazy = true)
    private static final DefaultPropertySource instance = new DefaultPropertySource();

    public DefaultPropertySource() {
        this.delegate = PropertySource.join(
                EnvironmentVariablesSource.INSTANCE, new SystemPropertiesSource(),
                new ApplicationEnvironmentPropertiesSource(),
                new ApplicationPropertiesSource());
    }

    private final PropertySource delegate;

    @Override
    public String get(String name) {
        return delegate.get(name);
    }
}
