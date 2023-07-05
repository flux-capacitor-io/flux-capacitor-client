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

import org.slf4j.LoggerFactory;

import static java.util.Optional.ofNullable;

public interface Encryption {

    static Encryption getEncryptionForEnvironment() {
        return ofNullable(System.getenv("ENCRYPT_KEY"))
                .or(() -> ofNullable(System.getenv("encrypt_key")))
                .<Encryption>map(encodedKey -> {
                    try {
                        return new DefaultEncryption(encodedKey);
                    } catch (Exception e) {
                        LoggerFactory.getLogger(Encryption.class).warn(
                                "Could not construct DefaultEncryption from environment variable `ENCRYPT_KEY`");
                        return null;
                    }
                }).orElse(NoOpEncryption.instance);
    }

    String encrypt(String value);

    String decrypt(String value);

}
