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

import static org.junit.jupiter.api.Assertions.assertEquals;

abstract class AbstractEncryptionTest {

    private final Encryption subject = createSubject();

    protected abstract Encryption createSubject();

    protected abstract Encryption createSubject(String encryptionKey);

    @Test
    void encryptString() {
        String input = "to be encrypted using ChaCha20";
        String cipherText = subject.encrypt(input);
        String output = subject.decrypt(cipherText);
        assertEquals(input, output);
    }

    @Test
    void decodeSecretKey() {
        var input = subject.getEncryptionKey();
        var copy = createSubject(input);
        assertEquals(input, copy.getEncryptionKey());
    }
}