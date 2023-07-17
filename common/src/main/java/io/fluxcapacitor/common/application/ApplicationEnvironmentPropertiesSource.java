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
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.encryption.Encryption;

import java.util.Optional;
import java.util.Properties;

import static java.util.Optional.ofNullable;

public class ApplicationEnvironmentPropertiesSource extends DecryptingPropertySource {

    public ApplicationEnvironmentPropertiesSource(Encryption encryption) {
        this(getEnvironment(), encryption);
    }

    public ApplicationEnvironmentPropertiesSource(String environment, Encryption encryption) {
        super(loadProperties(environment), encryption);
    }

    protected static Properties loadProperties(String environment) {
        return Optional.ofNullable(environment)
                .map(e -> String.format("/application-%s.properties", e))
                .flatMap(FileUtils::tryLoadFile).map(ObjectUtils::asProperties)
                .orElseGet(Properties::new);
    }

    protected static String getEnvironment() {
        return ofNullable(System.getenv("ENVIRONMENT"))
                .or(() -> ofNullable(System.getenv("environment")))
                .or(() -> ofNullable(System.getProperty("ENVIRONMENT")))
                .or(() -> ofNullable(System.getProperty("environment")))
                .orElse(null);
    }
}
