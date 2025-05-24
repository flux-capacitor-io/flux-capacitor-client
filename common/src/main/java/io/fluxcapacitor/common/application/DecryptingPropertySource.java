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

import java.util.Optional;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.util.Optional.ofNullable;

/**
 * A {@link PropertySource} decorator that transparently decrypts encrypted property values.
 *
 * <p>This implementation wraps a delegate {@link PropertySource} and attempts to decrypt any retrieved
 * property value using a configured {@link Encryption} strategy.
 *
 * <p>Decryption is only applied to values identified as encrypted via {@link Encryption#isEncrypted(String)}.
 * Values that are not encrypted are returned as-is.
 *
 * <p>The encryption strategy is determined using the {@code ENCRYPTION_KEY} environment variable or system property.
 * If no key is found, a no-op fallback is used, meaning decryption will be skipped.
 *
 * <p>To enable encrypted property support in a Flux application, ensure the appropriate key is present, e.g.:
 * <pre>{@code
 *   export ENCRYPTION_KEY=ChaCha20|mYbAse64ENcodedKeY==
 * }</pre>
 *
 * @see Encryption
 * @see DefaultEncryption
 */
@Slf4j
public class DecryptingPropertySource implements PropertySource {
    private final PropertySource delegate;

    /**
     * Returns the {@link Encryption} instance used by this property source.
     */
    @Getter
    private final Encryption encryption;

    private final Function<String, String> decryptionCache = memoize(s -> getEncryption().decrypt(s));

    /**
     * Constructs a {@code DecryptingPropertySource} using a delegate and automatically resolves the encryption key from
     * system or environment variables.
     * <p>It looks for the {@code ENCRYPTION_KEY} or {@code encryption_key} in the following order:
     * <ul>
     *   <li>Environment variable {@code ENCRYPTION_KEY}</li>
     *   <li>Environment variable {@code encryption_key}</li>
     *   <li>System property {@code ENCRYPTION_KEY}</li>
     *   <li>System property {@code encryption_key}</li>
     * </ul>
     *
     * <p>If no key is found, a no-op encryption fallback is used.
     *
     * @param delegate the property source to wrap
     */
    public DecryptingPropertySource(PropertySource delegate) {
        this(delegate, ofNullable(System.getenv("ENCRYPTION_KEY"))
                .or(() -> ofNullable(System.getenv("encryption_key")))
                .or(() -> ofNullable(System.getProperty("ENCRYPTION_KEY")))
                .or(() -> ofNullable(System.getProperty("encryption_key"))).orElse(null));
    }

    /**
     * Constructs a {@code DecryptingPropertySource} using the given encryption key.
     * <p>If the provided key is invalid or unrecognized, a no-op encryption fallback is used.
     *
     * @param delegate      the property source to wrap
     * @param encryptionKey the Base64-encoded encryption key in the form {@code algorithm|key}
     */
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
                }).orElseGet(() -> new DefaultEncryption(NoOpEncryption.INSTANCE)));
    }

    /**
     * Constructs a {@code DecryptingPropertySource} using an explicit {@link Encryption} strategy.
     *
     * @param delegate   the property source to wrap
     * @param encryption the encryption strategy to apply to values
     */
    public DecryptingPropertySource(PropertySource delegate, Encryption encryption) {
        this.delegate = delegate;
        this.encryption = encryption;
    }

    /**
     * Returns the decrypted value of the given property name.
     *
     * <p>If the underlying property is encrypted (i.e., {@link Encryption#isEncrypted(String)} is {@code true}),
     * it is decrypted using the configured {@link Encryption} implementation.
     * Otherwise, the raw value is returned unchanged.
     *
     * <p>Values are cached after the first decryption attempt for efficiency.
     *
     * @param name the property key
     * @return the decrypted or original property value, or {@code null} if not found
     */
    @Override
    public String get(String name) {
        String value = delegate.get(name);
        return value == null ? null : decryptionCache.apply(value);
    }
}
