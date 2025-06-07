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
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of the {@link Encryption} interface used in the Flux platform.
 * <p>
 * This implementation delegates encryption and decryption to an underlying algorithm-specific
 * {@link Encryption} strategy (e.g. {@link ChaCha20Poly1305Encryption}), while wrapping the result
 * in a recognizable format:
 * <pre>{@code
 * encrypted|<algorithm>|<ciphertext>
 * }</pre>
 *
 * <p>This format allows the platform to:
 * <ul>
 *   <li>Identify encrypted values consistently</li>
 *   <li>Support multiple algorithms in the future</li>
 *   <li>Perform decryption only when the encryption algorithm matches the delegate</li>
 * </ul>
 *
 * <p>The encryption key is expected to be prefixed with the algorithm, separated by a pipe character.
 * For example:
 * <pre>{@code
 * ChaCha20|AbcdEfGhIjKlMnOpQrStUvWxYz123456
 * }</pre>
 *
 * <p>Decryption is only attempted if the algorithm in the encrypted value matches the configured algorithm.
 * If it does not match (e.g. due to a missing or incorrect key), {@code null} is returned to indicate
 * that the value cannot be decrypted.
 *
 * @see ChaCha20Poly1305Encryption
 * @see #fromEncryptionKey(String)
 * @see #generateNewEncryptionKey()
 */
@Slf4j
@AllArgsConstructor
public class DefaultEncryption implements Encryption {

    /**
     * Generates a new encryption key using the default encryption mechanism.
     * The key is composed of the encryption algorithm identifier and the underlying encryption key,
     * formatted as a concatenated string.
     *
     * @return a string representation of the newly generated encryption key
     */
    public static String generateNewEncryptionKey() {
        return new DefaultEncryption().getEncryptionKey();
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    public static Encryption fromEncryptionKey(@NonNull String encryptionKey) {
        var algorithmAndKey = encryptionKey.split("\\|", 2);
        if (algorithmAndKey.length != 2) {
            throw new IllegalArgumentException("Encryption key is missing algorithm");
        }
        return switch (algorithmAndKey[0]) {
            case ChaCha20Poly1305Encryption.ALGORITHM -> new DefaultEncryption(
                    new ChaCha20Poly1305Encryption(algorithmAndKey[1]));
            default -> throw new IllegalArgumentException("Unknown encryption algorithm: " + algorithmAndKey[0]);
        };
    }

    public DefaultEncryption() {
        this(new ChaCha20Poly1305Encryption());
    }

    private final Encryption delegate;

    @Override
    public String encrypt(String value) {
        return String.format("encrypted|%s|%s", getAlgorithm(), delegate.encrypt(value));
    }

    @Override
    public String decrypt(String value) {
        if (value != null && isEncrypted(value)) {
            if (isEncryptedWithKnownAlgorithm(value)) {
                return delegate.decrypt(value.split("encrypted\\|" + getAlgorithm() + "\\|")[1]);
            }
            //Value is encrypted but with a different algorithm. Typically, this is the result of a
            // missing encryption key. Return null.
            return null;
        }
        return value;
    }

    protected boolean isEncryptedWithKnownAlgorithm(String value) {
        return value.startsWith("encrypted|" + getAlgorithm() + "|");
    }

    @Override
    public boolean isEncrypted(String value) {
        return value.startsWith("encrypted|");
    }

    @Override
    public String getAlgorithm() {
        return delegate.getAlgorithm();
    }

    @Override
    public String getEncryptionKey() {
        return getAlgorithm() + "|" + delegate.getEncryptionKey();
    }
}
