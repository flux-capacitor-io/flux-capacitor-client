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
 * Defines a contract for encrypting and decrypting sensitive values within the system.
 * <p>
 * This interface is commonly used by property sources and serializers to handle secure values,
 * ensuring that sensitive data like passwords, tokens, or credentials are stored and transmitted
 * in encrypted form.
 *
 * <p>Implementations of this interface are responsible for both the encryption logic and for
 * identifying whether a given value is already encrypted.
 *
 * <p>Typical usage includes:
 * <ul>
 *     <li>Encrypting properties before persisting or transmitting them</li>
 *     <li>Decrypting properties during configuration loading or message handling</li>
 *     <li>Checking if a value needs to be decrypted or should remain untouched</li>
 * </ul>
 */
public interface Encryption {

    /**
     * Encrypts the given plain-text value using the configured encryption algorithm and key.
     *
     * @param value the plain-text value to encrypt
     * @return the encrypted form of the input value
     */
    String encrypt(String value);

    /**
     * Decrypts the given encrypted value.
     * <p>
     * This method assumes that the input is a properly formatted encrypted value produced
     * by the corresponding {@link #encrypt(String)} method.
     *
     * @param value the encrypted string to decrypt
     * @return the decrypted plain-text value
     */
    String decrypt(String value);

    /**
     * Returns the name or identifier of the encryption algorithm used.
     *
     * @return the name of the algorithm (e.g., "AES", "RSA", "noop")
     */
    String getAlgorithm();

    /**
     * Returns the encryption key used by this implementation.
     *
     * @return the configured key for encryption/decryption, usually a secret or secret reference
     */
    String getEncryptionKey();

    /**
     * Returns {@code true} if the given value is considered encrypted by this implementation.
     * <p>
     * Useful for avoiding double-encryption or determining if decryption is required.
     *
     * @param value the value to check
     * @return {@code true} if the value appears to be encrypted, {@code false} otherwise
     */
    boolean isEncrypted(String value);
}
