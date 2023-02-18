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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.FilterContent;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.casting.Downcast;
import io.fluxcapacitor.javaclient.common.serialization.casting.Upcast;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.serialization.JsonUtils.valueToTree;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonSerializerTest {
    private static final String TYPE =
            "io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializerTest$RevisedObject";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JacksonSerializer serializer = new JacksonSerializer(List.of(new CasterStub()));

    @Test
    void testSerialization() {
        Data<byte[]> data = serializer.serialize(new RevisedObject("test", 42));
        assertEquals(TYPE, data.getType());
        assertEquals(3, data.getRevision());
    }

    @Test
    void testDeserializeWithoutUpcasting() {
        RevisedObject testObject = new RevisedObject("test", 42);
        assertEquals(testObject, serializer.deserialize(serializer.serialize(testObject)));
    }

    @Test
    void testDeserializeWithUpcasting() throws JsonProcessingException {
        RevisedObject expected = new RevisedObject("test", 5);
        assertEquals(expected, serializer.deserialize(createRev0Data("test")));
    }

    @Test
    void testDeserializeStream() throws JsonProcessingException {
        List<RevisedObject> expected = asList(new RevisedObject("test0", 5), new RevisedObject("test2", 42));
        List<?> actual = serializer.deserialize(
                        Stream.of(createRev0Data(expected.get(0).getName()), serializer.serialize(expected.get(1))), true)
                .map(DeserializingObject::getPayload)
                .collect(Collectors.toList());
        assertEquals(expected, actual);
    }

    @Test
    @SuppressWarnings("ResultOfMethodCallIgnored")
    void testFailOnUnknownType() {
        assertThrows(SerializationException.class,
                     () -> serializer.deserialize(Stream.of(new Data<>("bla".getBytes(), "unknownType", 0,
                                                                       "application/json")), true)
                             .collect(Collectors.toList()));
    }

    @Test
    void testReturnsJsonNodeIfTypeUnknownAndFailFlagIsOff() throws JsonProcessingException {
        Data<byte[]> data =
                new Data<>(objectMapper.writeValueAsBytes(new Foo("bar")), "unknownType", 0, "application/json");
        List<DeserializingObject<byte[], Data<byte[]>>> result =
                serializer.deserialize(Stream.of(data), false).toList();
        assertTrue(JsonNode.class.isAssignableFrom(result.get(0).getPayloadClass()));
        assertEquals(new ObjectNode(objectMapper.getNodeFactory(), singletonMap("foo", TextNode.valueOf("bar"))),
                     result.get(0).getPayload());
    }

    @Test
    void testConvertGetPayloadAs() throws JsonProcessingException {
        Foo foo = new Foo("bar");
        Data<byte[]> data = new Data<>(objectMapper.writeValueAsBytes(foo), "unknownType", 0, "application/json");
        List<DeserializingObject<byte[], Data<byte[]>>> result =
                serializer.deserialize(Stream.of(data), false).toList();
        assertEquals(foo, result.get(0).getPayloadAs(Foo.class));
    }

    @Test
    void testDeserializeTypedCollection() {
        List<Foo> input = asList(new Foo("bla1"), new Foo("bla2"));
        assertEquals(input, serializer.deserialize(serializer.serialize(input)));
    }

    @Test
    void testDeserializeTypedMap() {
        Map<String, Foo> input = new HashMap<>();
        input.put("key1", new Foo("foo1"));
        input.put("key2", new Foo("foo2"));
        Object output = serializer.deserialize(serializer.serialize(input));
        assertEquals(input, output);
    }

    @Test
    void testDeserializeMixedCollection() {
        List<?> input = Arrays.asList(new Foo("bla1"), "bla2");
        assertEquals(objectMapper.convertValue(input, Object.class),
                     serializer.deserialize(serializer.serialize(input)));
    }

    @Nested
    class CloningTests {
        @Test
        void testCloningObject() {
            Foo input = new Foo("bar");
            Foo output = serializer.clone(input);
            assertNotSame(input, output);
            assertEquals(input, output);
        }

        @Test
        void testCloningComplexObject() {
            Object input = new ComplexObject(new ComplexObject.Child("bar", 123), 0.23);
            Object output = serializer.clone(input);
            assertNotSame(input, output);
            assertEquals(input, output);
        }
    }

    @Nested
    class DownCastingTests {
        @Test
        void downcastToIntermediateRevision() {
            Object result = serializer.downcast(new RevisedObject("bla", 42), 1);
            assertEquals(valueToTree(Map.of("n", "bla", "someInteger", 42)), result);
        }

        @Test
        void downcastAllTheWay() {
            Object result = serializer.downcast(new RevisedObject("bla", 42), 0);
            assertEquals(valueToTree(Map.of("n", "bla")), result);
        }

        @Test
        void downcastData() {
            Object result = serializer.downcast(
                    new Data<>(valueToTree(Map.of("n", "bla", "someInteger", 42)),
                               RevisedObject.class.getName(), 1), 0);
            assertEquals(valueToTree(Map.of("n", "bla")), result);
        }
    }

    @Nested
    class ContentFilterTests {

        private final ComplexObject complex = new ComplexObject(new ComplexObject.Child("bar", 123), 0.23);

        @Test
        void filterFieldFromChild() {
            var output = serializer.filterContent(complex, new MockUser("normal"));
            assertEquals(123, output.getChild().getNumber());
            assertNull(output.getChild().getFoo());
        }

        @Test
        void removeNestedObject() {
            var output = serializer.filterContent(complex, new MockUser("unfit"));
            assertNull(output.getChild());
        }

        @Test
        void dontFilterFieldIfAdmin() {
            var output = serializer.filterContent(complex, new MockUser("admin"));
            assertEquals(complex, output);
        }

        @Test
        void removeFromArray() {
            var output = serializer.filterContent(List.of(complex.getChild()), new MockUser("unfit"));
            assertTrue(output.isEmpty());
        }
    }

    @Value
    static class ComplexObject {
        Child child;
        double dbl;

        @Value
        @AllArgsConstructor
        @Builder(toBuilder = true)
        static class Child {
            String foo;
            int number;

            @FilterContent
            Child removeFoo(User viewer) {
                return viewer.hasRole("admin") ? this : viewer.hasRole("unfit") ? null : toBuilder().foo(null).build();
            }
        }
    }


    private Data<byte[]> createRev0Data(String name) throws JsonProcessingException {
        ObjectNode rev0Payload = new ObjectNode(objectMapper.getNodeFactory());
        rev0Payload.put("n", name);
        return new Data<>(objectMapper.writeValueAsBytes(rev0Payload), TYPE, 0, "application/json");
    }

    @Revision(3)
    @Value
    private static class RevisedObject {
        String name;
        int someInteger;
    }

    public static class CasterStub {
        @Upcast(type = TYPE, revision = 0)
        public ObjectNode upcastFrom0(ObjectNode input) {
            return input.put("someInteger", 5);
        }

        @Upcast(type = TYPE, revision = 1)
        public ObjectNode upcastFrom1(ObjectNode input) {
            return input.put("name", input.remove("n").asText());
        }

        @Upcast(type = TYPE, revision = 2)
        public Data<ObjectNode> upcastFrom2(Data<ObjectNode> input) {
            return new Data<>(input.getValue(), input.getType(), 3, "application/json");
        }

        @Downcast(type = TYPE, revision = 3)
        public Data<ObjectNode> downcastFrom3(Data<ObjectNode> input) {
            return new Data<>(input.getValue(), input.getType(), 2, "application/json");
        }

        @Downcast(type = TYPE, revision = 2)
        public ObjectNode downcastFrom2(ObjectNode input) {
            return input.put("n", input.remove("name").asText());
        }

        @Downcast(type = TYPE, revision = 1)
        public ObjectNode downcastFrom1(ObjectNode input) {
            return input.without("someInteger");
        }
    }

    @Value
    private static class Foo {
        String foo;
    }

}