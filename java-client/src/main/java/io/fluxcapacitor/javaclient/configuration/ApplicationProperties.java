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
import io.fluxcapacitor.common.encryption.Encryption;
import io.fluxcapacitor.javaclient.FluxCapacitor;

import java.util.Optional;

public class ApplicationProperties {

    public static String getProperty(String name) {
        return getPropertySource().get(name);
    }

    public static boolean getBooleanProperty(String name) {
        return getBooleanProperty(name, false);
    }

    public static boolean getBooleanProperty(String name, boolean defaultValue) {
        return Optional.ofNullable(getProperty(name)).map("true"::equalsIgnoreCase).orElse(defaultValue);
    }

    public static Integer getIntegerProperty(String name) {
        return getIntegerProperty(name, null);
    }

    public static Integer getIntegerProperty(String name, Integer defaultValue) {
        return Optional.ofNullable(getProperty(name)).map(Integer::valueOf).orElse(defaultValue);
    }

    public static String getProperty(String name, String defaultValue) {
        return Optional.ofNullable(getProperty(name)).orElse(defaultValue);
    }

    public static String requireProperty(String name) {
        return Optional.ofNullable(getProperty(name)).orElseThrow(
                () -> new IllegalStateException(String.format("Property for %s is missing", name)));
    }

    public static boolean containsProperty(String name) {
        return getProperty(name) != null;
    }

    public static String substituteProperties(String template) {
        return getPropertySource().substituteProperties(template);
    }

    public static Encryption getEncryption() {
        return getPropertySource().getEncryption();
    }

    public static String encryptValue(String value) {
        return getEncryption().encrypt(value);
    }

    public static String decryptValue(String encryptedValue) {
        return getEncryption().decrypt(encryptedValue);
    }

    static DecryptingPropertySource getPropertySource() {
        return FluxCapacitor.getOptionally().map(FluxCapacitor::propertySource)
                .map(p -> p instanceof DecryptingPropertySource dps ? dps : new DecryptingPropertySource(p))
                .orElseGet(() -> new DecryptingPropertySource(DefaultPropertySource.getInstance()));
    }

}
