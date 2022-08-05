/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.common.reflection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.hasProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.writeProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionUtilsTest {

    private final MockObject someObject =
            MockObject.builder().propertyWithGetter("thisHasGetter").propertyWithoutGetter("thisHasNoGetter")
                    .child(MockObject.builder().propertyWithGetter("childGetter").propertyWithoutGetter("childNoGetter")
                                   .child(MockObject.builder().propertyWithoutGetter(null)
                                                  .propertyWithGetter("grandChildGetter").build()).build())
                    .child2(MockObject.builder().propertyWithoutGetter("child2Field").build()).build();

    @Nested
    class ReadTest {
        @Test
        void testReadingRootGetter() {
            assertEquals(someObject.getPropertyWithGetter(),
                         readProperty("propertyWithGetter", someObject).orElseThrow());
        }

        @Test
        void testReadingRootField() {
            assertEquals(someObject.propertyWithoutGetter,
                         readProperty("propertyWithoutGetter", someObject).orElseThrow());
        }

        @Test
        void testReadingChildGetter() {
            assertEquals(someObject.getChild().getPropertyWithGetter(),
                         readProperty("child/propertyWithGetter", someObject).orElseThrow());
        }

        @Test
        void testReadingChildField() {
            assertTrue(hasProperty("child/propertyWithoutGetter", someObject));
            assertEquals(someObject.getChild().propertyWithoutGetter,
                         readProperty("child/propertyWithoutGetter", someObject).orElseThrow());
            assertEquals(someObject.child2.propertyWithoutGetter,
                         readProperty("child2/propertyWithoutGetter", someObject).orElseThrow());
        }

        @Test
        void testReadingGrandChild() {
            assertTrue(hasProperty("child/child/propertyWithGetter", someObject));
            assertTrue(hasProperty("child/child/propertyWithoutGetter", someObject));
            assertEquals(someObject.getChild().getChild().getPropertyWithGetter(),
                         readProperty("child/child/propertyWithGetter", someObject).orElseThrow());
            assertTrue(readProperty("child/child/propertyWithoutGetter", someObject).isEmpty());
        }

        @Test
        void testReadingUnknownProperty() {
            assertFalse(hasProperty("unknown", someObject));
            assertFalse(hasProperty("child/unknown2", someObject));
            assertFalse(hasProperty("unknown1/unknown2", someObject));
            assertTrue(readProperty("unknownGetter", someObject).isEmpty());
        }
    }

    @Nested
    class WriteTest {
        private final String testString = "test";

        @Test
        void testWriteRootSetter() {
            writeProperty("propertyWithGetter", someObject, testString);
            assertEquals(testString, readProperty("propertyWithGetter", someObject).orElseThrow());
        }

        @Test
        void testWriteRootField() {
            writeProperty("propertyWithoutGetter", someObject, testString);
            assertEquals(testString, readProperty("propertyWithoutGetter", someObject).orElseThrow());
        }

        @Test
        void testWritingChildGetter() {
            writeProperty("child/propertyWithGetter", someObject, testString);
            assertEquals(testString, readProperty("child/propertyWithGetter", someObject).orElseThrow());
        }

        @Test
        void testWritingChildField() {
            writeProperty("child/propertyWithoutGetter", someObject, testString);
            assertEquals(testString, readProperty("child/propertyWithoutGetter", someObject).orElseThrow());

            writeProperty("child2/propertyWithoutGetter", someObject, testString);
            assertEquals(testString, readProperty("child2/propertyWithoutGetter", someObject).orElseThrow());
        }

        @Test
        void testWritingGrandChild() {
            writeProperty("child/child/propertyWithGetter", someObject, testString);
            assertEquals(testString, readProperty("child/child/propertyWithGetter", someObject).orElseThrow());
        }

        @Test
        void testWritingMissingGrandChild() {
            writeProperty("child.child.propertyWithoutGetter", someObject, testString);
            assertEquals(testString, readProperty("child/child/propertyWithoutGetter", someObject).orElseThrow());
        }

        @Test
        void testWritingUnknownPropertyIgnored() {
            writeProperty("unknownProperty", someObject, testString);
            assertFalse(hasProperty("unknownProperty", someObject));
            writeProperty("child/child/unknownProperty2", someObject, testString);
        }

        @Test
        void testWriteToObjectNode() {
            ObjectNode node = JsonUtils.valueToTree(someObject);
            writeProperty("child/child/propertyWithGetter", node, testString);
            assertEquals(testString,
                         ReflectionUtils.<JsonNode>readProperty("child/child/propertyWithGetter", node)
                                 .map(JsonNode::asText).orElseThrow());
        }

        @Test
        void testWritingMissingObjectNode() {
            ObjectNode node = JsonUtils.valueToTree(someObject);
            writeProperty("child.child.propertyWithoutGetter", node, testString);
            assertEquals(testString, ReflectionUtils.<JsonNode>readProperty(
                    "child/child/propertyWithoutGetter", node).map(JsonNode::asText).orElseThrow());
        }
    }


    @Value
    @Builder(toBuilder = true)
    private static class MockObject {
        @NonFinal
        @Setter
        String propertyWithGetter;
        @Getter(AccessLevel.NONE)
        String propertyWithoutGetter;
        MockObject child;
        @Getter(AccessLevel.NONE)
        MockObject child2;

        public String getOther() {
            return "someOtherValue";
        }
    }
}