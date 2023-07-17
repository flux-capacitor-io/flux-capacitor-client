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

import io.fluxcapacitor.common.encryption.DefaultEncryption;
import io.fluxcapacitor.common.encryption.Encryption;
import io.fluxcapacitor.common.encryption.NoOpEncryption;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.util.Optional.ofNullable;

@Slf4j
public class DefaultPropertySource implements PropertySource {
    @Getter
    private static final DefaultPropertySource instance = new DefaultPropertySource();

    public DefaultPropertySource() {
        this(ofNullable(System.getenv("ENCRYPT_KEY"))
                     .or(() -> ofNullable(System.getenv("encrypt_key")))
                     .map(encodedKey -> {
                         try {
                             return DefaultEncryption.fromEncryptionKey(encodedKey);
                         } catch (Exception e) {
                             log.error("Could not construct DefaultEncryption from environment variable `ENCRYPT_KEY`");
                             return NoOpEncryption.instance;
                         }
                     }).orElse(NoOpEncryption.instance));
    }

    public DefaultPropertySource(String encryptionKey) {
        this(DefaultEncryption.fromEncryptionKey(encryptionKey));
    }

    public DefaultPropertySource(Encryption encryption) {
        this.encryption = encryption;
        this.delegate = PropertySource.join(
                EnvironmentVariablesSource.instance, new SystemPropertiesSource(encryption),
                new ApplicationEnvironmentPropertiesSource(encryption),
                new ApplicationPropertiesSource(encryption));
    }

    @Getter
    private final Encryption encryption;
    private final PropertySource delegate;

    @Override
    public String get(String name) {
        return delegate.get(name);
    }
}
