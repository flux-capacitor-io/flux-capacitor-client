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

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChaCha20Poly1305EncryptionTest {

    @Test
    void encryptString() {
        var key = ChaCha20Poly1305Encryption.generateSecretKey();
        String input = "to be encrypted using ChaCha20";
        String cipherText = ChaCha20Poly1305Encryption.encrypt(input, key);
        String output = ChaCha20Poly1305Encryption.decrypt(cipherText, key);
        assertEquals(input, output);
    }

    @Test
    void decodeSecretKey() {
        SecretKey input = ChaCha20Poly1305Encryption.generateSecretKey();
        String encoded = ChaCha20Poly1305Encryption.encodeKey(input);
        var output = ChaCha20Poly1305Encryption.decodeKey(encoded);
        assertArrayEquals(input.getEncoded(), output.getEncoded());
    }
}