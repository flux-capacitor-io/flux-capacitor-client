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

package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.application.DecryptingPropertySource;
import io.fluxcapacitor.common.application.DefaultPropertySource;
import io.fluxcapacitor.common.application.PropertySource;
import io.fluxcapacitor.common.encryption.Encryption;
import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.util.Optional;

/**
 * Central utility for resolving configuration properties within a Flux application.
 *
 * <p>This class delegates to a layered {@link PropertySource}, typically obtained from the active
 * {@link io.fluxcapacitor.javaclient.FluxCapacitor} instance. If no context-bound property source is present, it falls
 * back to a {@link DecryptingPropertySource} that wraps the default layered {@link DefaultPropertySource}.
 *
 * <p>Property resolution supports typed access, default values, encryption, and template substitution.
 *
 * <p>Common usage:
 * <pre>{@code
 * String token = ApplicationProperties.getProperty("FLUX_API_TOKEN");
 * boolean featureEnabled = ApplicationProperties.getBooleanProperty("my.feature.enabled", true);
 * }</pre>
 *
 * @see PropertySource
 * @see DefaultPropertySource
 * @see DecryptingPropertySource
 */
public class ApplicationProperties {

    /**
     * Returns the raw string property for the given key, or {@code null} if not found.
     */
    public static String getProperty(String name) {
        return getPropertySource().get(name);
    }

    /**
     * Resolves a boolean property by key, returning {@code false} if not present.
     * <p>Accepts case-insensitive "true" as {@code true}, otherwise returns {@code false}.
     */
    public static boolean getBooleanProperty(String name) {
        return getBooleanProperty(name, false);
    }

    /**
     * Resolves a boolean property by key, returning a default if the property is not present.
     */
    public static boolean getBooleanProperty(String name, boolean defaultValue) {
        return Optional.ofNullable(getProperty(name)).map("true"::equalsIgnoreCase).orElse(defaultValue);
    }

    /**
     * Resolves an integer property by key, or {@code null} if not found.
     *
     * @throws NumberFormatException if the property value is not a valid integer
     */
    public static Integer getIntegerProperty(String name) {
        return getIntegerProperty(name, null);
    }

    /**
     * Resolves an integer property by key, or returns the given default value if not found.
     *
     * @throws NumberFormatException if the property value is not a valid integer
     */
    public static Integer getIntegerProperty(String name, Integer defaultValue) {
        return Optional.ofNullable(getProperty(name)).map(Integer::valueOf).orElse(defaultValue);
    }

    /**
     * Returns the string property value for the given key, or the specified default if not found.
     */
    public static String getProperty(String name, String defaultValue) {
        return Optional.ofNullable(getProperty(name)).orElse(defaultValue);
    }

    /**
     * Returns the string property for the given key, throwing an {@link IllegalStateException} if not found.
     */
    public static String requireProperty(String name) {
        return Optional.ofNullable(getProperty(name)).orElseThrow(
                () -> new IllegalStateException(String.format("Property for %s is missing", name)));
    }

    /**
     * Returns {@code true} if a property with the given name exists.
     */
    public static boolean containsProperty(String name) {
        return getProperty(name) != null;
    }

    /**
     * Substitutes placeholders in the given template using current property values.
     * <p>Placeholders use the syntax {@code ${propertyName}}.
     */
    public static String substituteProperties(String template) {
        return getPropertySource().substituteProperties(template);
    }

    /**
     * Returns the currently active {@link Encryption} instance.
     * <p>By default, wraps the encryption from the current {@link PropertySource}.
     */
    public static Encryption getEncryption() {
        return getPropertySource().getEncryption();
    }

    /**
     * Encrypts the given value using the configured {@link Encryption} strategy.
     */
    public static String encryptValue(String value) {
        return getEncryption().encrypt(value);
    }

    /**
     * Decrypts the given encrypted value using the configured {@link Encryption} strategy.
     * <p>Returns the original value if decryption is not applicable.
     */
    public static String decryptValue(String encryptedValue) {
        return getEncryption().decrypt(encryptedValue);
    }

    static DecryptingPropertySource getPropertySource() {
        return FluxCapacitor.getOptionally().map(FluxCapacitor::propertySource)
                .map(p -> p instanceof DecryptingPropertySource dps ? dps : new DecryptingPropertySource(p))
                .orElseGet(() -> new DecryptingPropertySource(DefaultPropertySource.getInstance()));
    }

}
