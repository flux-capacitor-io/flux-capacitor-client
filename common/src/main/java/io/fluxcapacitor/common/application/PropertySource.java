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

import java.util.Arrays;
import java.util.Optional;

@FunctionalInterface
public interface PropertySource {
    String get(String name);

    default boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    default boolean getBoolean(String name, boolean defaultValue) {
        return Optional.ofNullable(get(name)).map("true"::equalsIgnoreCase).orElse(defaultValue);
    }

    default String get(String name, String defaultValue) {
        return Optional.ofNullable(get(name)).orElse(defaultValue);
    }

    default String require(String name) {
        return Optional.ofNullable(get(name)).orElseThrow(
                () -> new IllegalStateException(String.format("Property for %s is missing", name)));
    }

    default boolean containsProperty(String name) {
        return get(name) != null;
    }

    default PropertySource merge(PropertySource next) {
        return name -> Optional.ofNullable(PropertySource.this.get(name)).orElseGet(() -> next.get(name));
    }

    static PropertySource join(PropertySource... propertySources) {
        return Arrays.stream(propertySources).reduce(PropertySource::merge).orElse(NoOpPropertySource.instance);
    }
}
