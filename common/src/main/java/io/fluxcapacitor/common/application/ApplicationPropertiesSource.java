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

import io.fluxcapacitor.common.ObjectUtils;

import java.util.Properties;

import static io.fluxcapacitor.common.FileUtils.tryLoadFile;

public class ApplicationPropertiesSource implements PropertySource {
    private final Properties properties;

    public ApplicationPropertiesSource() {
        this("/application.properties");
    }

    public ApplicationPropertiesSource(String path) {
        properties = tryLoadFile(path).map(ObjectUtils::asProperties).orElseGet(Properties::new);
    }

    @Override
    public String get(String name) {
        return (String) properties.get(name);
    }
}
