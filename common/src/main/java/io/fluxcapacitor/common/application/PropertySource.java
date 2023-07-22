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

import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FunctionalInterface
public interface PropertySource {

    Pattern substitutionPattern = Pattern.compile("[$][{]([^${}]+)}");

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

    default String substituteProperties(String template) {
        Matcher matcher = substitutionPattern.matcher(template);
        StringBuilder resultBuilder = new StringBuilder(template);
        List<Object> valueList = new ArrayList<>();
        while (matcher.find()) {
            String key = matcher.group(1);
            String paramName = "${" + key + "}";
            int index = resultBuilder.indexOf(paramName);
            if (index != -1) {
                resultBuilder.replace(index, index + paramName.length(), "%s");
                var keyWithDefault = key.split(":", 2);
                String value;
                if (keyWithDefault.length == 1) {
                    value = get(key);
                    if (value == null) {
                        LoggerFactory.getLogger(getClass()).warn("Property named \"{}\" hasn't been set", key);
                    }
                } else {
                    value = get(keyWithDefault[0], keyWithDefault[1]);
                }
                valueList.add(value == null ? "" : value);
            }
        }
        String result = String.format(resultBuilder.toString(), valueList.toArray());
        return result.equals(template) ? result : substituteProperties(result);
    }

    default PropertySource merge(PropertySource next) {
        return name -> Optional.ofNullable(PropertySource.this.get(name)).orElseGet(() -> next.get(name));
    }

    static PropertySource join(PropertySource... propertySources) {
        return Arrays.stream(propertySources).reduce(PropertySource::merge).orElse(NoOpPropertySource.instance);
    }
}
