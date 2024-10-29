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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdTest {

    private final Owner defaultOwner = new Owner();

    @Test
    void serializeId() {
        assertEquals("{\"id\":\"fooBar\"}", JsonUtils.asJson(defaultOwner));
    }

    @Test
    void deserializeId() {
        assertEquals(defaultOwner, JsonUtils.fromJson("{\"id\":\"fooBar\"}", Owner.class));
    }

    @Test
    void convert() {
        assertEquals(defaultOwner, JsonUtils.convertValue(defaultOwner, Owner.class));
    }

    @Test
    void toStringIsLowercaseAndHasPrefix() {
        assertEquals("mock-foobar", defaultOwner.getId().toString());
    }

    @Test
    void blankFunctionalIdNotAllowed() {
        assertThrows(ValidationException.class, () -> new MockId("  "));
    }

    @Test
    void blankFunctionalIdNotAllowed_deserialization() {
        assertThrows(Exception.class, () -> JsonUtils.convertValue("\" \"", MockId.class));
    }

    @Value
    @AllArgsConstructor
    private static class Owner {
        MockId id;

        public Owner() {
            this("fooBar");
        }

        public Owner(String id) {
           this(new MockId(id));
        }
    }

    private static class MockId extends Id<Owner> {
        protected MockId(String id) {
            super(id, Owner.class, "mock-", false);
        }
    }
}