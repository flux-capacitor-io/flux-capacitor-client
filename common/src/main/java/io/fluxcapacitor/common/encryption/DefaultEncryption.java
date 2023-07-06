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

package io.fluxcapacitor.common.encryption;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.crypto.SecretKey;

@AllArgsConstructor
public class DefaultEncryption implements Encryption {

    private static final String ChaCha20 = "ChaCha20";

    public static String generateSecretKeyString() {
        return ChaCha20Poly1305Encryption.generateSecretKeyString();
    }

    @SneakyThrows
    public static SecretKey generateSecretKey() {
        return ChaCha20Poly1305Encryption.generateSecretKey();
    }

    public static SecretKey decodeKey(String encodedKey) {
        return ChaCha20Poly1305Encryption.decodeKey(encodedKey);
    }

    @Getter
    private final SecretKey secretKey;

    public DefaultEncryption() {
        this(generateSecretKey());
    }

    public DefaultEncryption(String encodedKey) {
        this(ChaCha20Poly1305Encryption.decodeKey(encodedKey));
    }

    @Override
    public String encrypt(String value) {
        return ChaCha20 + "|" + ChaCha20Poly1305Encryption.encrypt(value, secretKey);
    }

    @Override
    public String decrypt(String value) {
        if (value != null && value.startsWith(ChaCha20 + "|")) {
            return ChaCha20Poly1305Encryption.decrypt(value.split(ChaCha20 + "\\|")[1], secretKey);
        }
        return value;
    }

    public String getSecretKeyString() {
        return ChaCha20Poly1305Encryption.encodeKey(secretKey);
    }
}
