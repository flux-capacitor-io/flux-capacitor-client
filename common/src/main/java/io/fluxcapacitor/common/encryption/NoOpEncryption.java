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

/**
 * A no-op implementation of the {@link Encryption} interface that performs no actual encryption or decryption.
 * <p>
 * This implementation simply returns the input value as-is, making it useful for testing, debugging, or
 * development environments where encryption is unnecessary or intentionally disabled.
 *
 * <h2>Usage Note:</h2>
 * This class should never be used in production systems where encryption or data confidentiality is required.
 */
public enum NoOpEncryption implements Encryption {
    INSTANCE;

    @Override
    public String encrypt(String value) {
        return value;
    }

    @Override
    public String decrypt(String value) {
        return value;
    }

    @Override
    public boolean isEncrypted(String value) {
        return false;
    }

    @Override
    public String getAlgorithm() {
        return "no-op";
    }

    @Override
    public String getEncryptionKey() {
        return "no-op";
    }
}
