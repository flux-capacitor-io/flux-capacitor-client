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
import io.fluxcapacitor.common.encryption.Encryption;
import lombok.SneakyThrows;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;

public abstract class DecryptingPropertySource implements PropertySource {

    private final Properties properties;
    private final Encryption encryption;

    @SneakyThrows
    public DecryptingPropertySource(Properties properties, Encryption encryption) {
        this.properties = ObjectUtils.copyOf(properties);
        this.encryption = encryption;
        for (Map.Entry<Object, Object> entry : new HashSet<>(properties.entrySet())) {
            if (entry.getValue() instanceof String string) {
                this.properties.setProperty((String) entry.getKey(), decrypt(string));
            }
        }
    }

    @Override
    public String get(String name) {
        return (String) properties.get(name);
    }

    public String decrypt(String value) {
        return encryption.decrypt(value);
    }
}
