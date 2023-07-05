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
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.TestUtils.runWithSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultPropertySourceTest {

    @Test
    void fromSystemProperty() {
        runWithSystemProperty(() -> assertEquals("bar", new DefaultPropertySource().get("foo")),
                              "foo", "bar");
    }

    @Test
    void fromApplicationPropertiesFile() {
        runWithSystemProperty(() -> {
            DefaultPropertySource source = new DefaultPropertySource();
            assertEquals("bar", source.get("propertiesFile.foo"));
            assertEquals("someOtherValue", source.get("foo"));
        }, "foo", "someOtherValue");
    }

    @Test
    void fromApplicationEnvironmentPropertiesFile() {
        runWithSystemProperty(() -> {
            var source = new DefaultPropertySource();
            assertEquals("envbar", source.get("propertiesFile.foo"));
            assertEquals("bar", source.get("envFile.foo"));
        }, "environment", "test");
    }

    @Test
    void decryptProperty() {
        DefaultEncryption encryption = new DefaultEncryption();
        var source = new DefaultPropertySource(encryption);
        runWithSystemProperty(() -> assertEquals("foo_encrypted", source.get("encrypted")),
                              "encrypted", encryption.encrypt("foo_encrypted"));
    }
}