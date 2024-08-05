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
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.util.Optional.ofNullable;

@AllArgsConstructor
@Slf4j
public class DecryptingPropertySource implements PropertySource {
    private final PropertySource delegate;
    @Getter
    private final Encryption encryption;
    private final Function<String, String> decryptionCache = memoize(s -> getEncryption().decrypt(s));

    public DecryptingPropertySource(PropertySource delegate) {
        this(delegate, ofNullable(System.getenv("ENCRYPTION_KEY"))
                     .or(() -> ofNullable(System.getenv("encryption_key")))
                     .or(() -> ofNullable(System.getProperty("ENCRYPTION_KEY")))
                     .or(() -> ofNullable(System.getProperty("encryption_key"))).orElse(null));
    }

    public DecryptingPropertySource(PropertySource delegate, String encryptionKey) {
        this(delegate, Optional.ofNullable(encryptionKey)
                .map(encodedKey -> {
                    try {
                        return DefaultEncryption.fromEncryptionKey(encodedKey);
                    } catch (Exception e) {
                        log.error("Could not construct DefaultEncryption from environment variable "
                                  + "`ENCRYPTION_KEY`");
                        return null;
                    }
                }).orElse(NoOpEncryption.INSTANCE));
    }


    @Override
    public String get(String name) {
        String value = delegate.get(name);
        return value == null ? null : decryptionCache.apply(value);
    }
}
