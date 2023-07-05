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

import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Adopted from <a href="https://github.com/java-crypto/cross_platform_crypto">
 *     https://github.com/java-crypto/cross_platform_crypto</a>
 */
public class ChaCha20Poly1305Encryption {

    private static final SecureRandom secureRandom = new SecureRandom();
    public static final String ALGORITHM = "ChaCha20";
    public static final String TRANSFORMATION = "ChaCha20-Poly1305/None/NoPadding";
    public static final int NONCE_LENGTH = 12;
    public static final int KEY_SIZE = 256;
    public static final int TAG_LENGTH = 16;

    public static String generateSecretKeyString() {
        return encodeKey(generateSecretKey());
    }

    public static String encodeKey(SecretKey secretKey) {
        return encode(secretKey.getEncoded());
    }

    @SneakyThrows
    public static SecretKey generateSecretKey() {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
        keyGenerator.init(KEY_SIZE);
        return keyGenerator.generateKey();
    }

    public static SecretKey decodeKey(String encodedKey) {
        return new SecretKeySpec(decode(encodedKey), ALGORITHM);
    }

    @SneakyThrows
    public static String encrypt(String value, SecretKey key) {
        IvParameterSpec ivParameterSpec = new IvParameterSpec(nextNonce());
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParameterSpec);
        byte[] ciphertextWithTag = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = new byte[(ciphertextWithTag.length - TAG_LENGTH)];
        byte[] tag = new byte[TAG_LENGTH];
        System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, (ciphertextWithTag.length - TAG_LENGTH));
        System.arraycopy(ciphertextWithTag, (ciphertextWithTag.length - TAG_LENGTH), tag, 0, TAG_LENGTH);
        return encode(ivParameterSpec.getIV()) + ":" + encode(ciphertext) + ":" + encode(tag);
    }

    @SneakyThrows
    public static String decrypt(String cipherText, SecretKey key) {
        String[] parts = cipherText.split(":", 0);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(decode(parts[0])));
        return new String(cipher.doFinal(concatenate(decode(parts[1]), decode(parts[2]))));
    }

    private static byte[] nextNonce() {
        byte[] newNonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(newNonce);
        return newNonce;
    }

    private static String encode(byte[] input) {
        return Base64.getEncoder().encodeToString(input);
    }

    private static byte[] decode(String input) {
        return Base64.getDecoder().decode(input);
    }

    private static byte[] concatenate(byte[] a, byte[] b) {
        return ByteBuffer.allocate(a.length + b.length).put(a).put(b).array();
    }

}
