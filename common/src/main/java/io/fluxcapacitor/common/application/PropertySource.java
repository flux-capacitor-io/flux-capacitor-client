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

/**
 * Represents a source of configuration properties.
 * <p>
 * This interface provides a unified way to access application-level configuration values. You can use it to retrieve
 * string, boolean, or substituted values and chain multiple sources together.
 * <p>
 * Implementations may load properties from system environment variables, property files, Spring contexts, application
 * {@code Entities}, etc.
 */
@FunctionalInterface
public interface PropertySource {

    /**
     * Regex pattern used for property substitution in the form <code>${property.name[:default]}</code>.
     */
    Pattern substitutionPattern = Pattern.compile("[$][{]([^${}]+)}");

    /**
     * Retrieves the value of a property by name.
     *
     * @param name the name of the property to look up
     * @return the property value, or {@code null} if not found
     */
    String get(String name);

    /**
     * Returns the value of the property as a boolean.
     *
     * @param name the name of the property to look up
     * @return {@code true} if the value is "true" (case-insensitive), {@code false} otherwise or if not present
     */
    default boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    /**
     * Returns the value of the property as a boolean, falling back to a default if not present.
     *
     * @param name         the name of the property
     * @param defaultValue the value to return if the property is not set
     * @return the parsed boolean value, or {@code defaultValue} if not present
     */
    default boolean getBoolean(String name, boolean defaultValue) {
        return Optional.ofNullable(get(name)).map("true"::equalsIgnoreCase).orElse(defaultValue);
    }

    /**
     * Returns the property value, or the given default if not present.
     *
     * @param name         the name of the property
     * @param defaultValue the fallback value to use if the property is not set
     * @return the property value or the default
     */
    default String get(String name, String defaultValue) {
        return Optional.ofNullable(get(name)).orElse(defaultValue);
    }

    /**
     * Retrieves the value of a property by name and throws if not present.
     *
     * @param name the name of the property
     * @return the property value
     * @throws IllegalStateException if the property is not found
     */
    default String require(String name) {
        return Optional.ofNullable(get(name))
                .orElseThrow(() -> new IllegalStateException(String.format("Property for %s is missing", name)));
    }

    /**
     * Checks if a property is defined.
     *
     * @param name the name of the property
     * @return {@code true} if the property is set, otherwise {@code false}
     */
    default boolean containsProperty(String name) {
        return get(name) != null;
    }

    /**
     * Substitutes all placeholders of the form <code>${property[:default]}</code> in the given template string.
     * <p>
     * Supports recursive substitutions and default fallback values (e.g. <code>${env:dev}</code>).
     *
     * @param template the template containing substitutions
     * @return the fully substituted string
     */
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
                        LoggerFactory.getLogger(getClass())
                                .warn("Property named \"{}\" hasn't been set", key);
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

    /**
     * Combines this property source with another, returning the first non-null value found between the two.
     * <p>
     * If a property is not present in this source, it will delegate to the next.
     *
     * @param next the fallback property source
     * @return a chained property source
     */
    default PropertySource andThen(PropertySource next) {
        return name -> Optional.ofNullable(PropertySource.this.get(name)).orElseGet(() -> next.get(name));
    }

    /**
     * Joins multiple {@code PropertySource} instances into a single one.
     * <p>
     * The returned property source will resolve properties using the order of the given sources.
     *
     * @param propertySources the sources to join
     * @return a combined {@code PropertySource}
     */
    static PropertySource join(PropertySource... propertySources) {
        return Arrays.stream(propertySources).reduce(PropertySource::andThen).orElse(NoOpPropertySource.INSTANCE);
    }
}
