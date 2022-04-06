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
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcast;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacksonSerializerTest {
    private static final String TYPE =
            "io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializerTest$RevisedObject";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JacksonSerializer serializer = new JacksonSerializer(Collections.singletonList(new RevisedObjectUpcaster()));

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
        assertThrows(SerializationException.class, () -> serializer.deserialize(Stream.of(new Data<>("bla".getBytes(), "unknownType", 0,
                                                                                                     "application/json")), true)
                .collect(Collectors.toList()));
    }

    @Test
    void testReturnsJsonNodeIfTypeUnknownAndFailFlagIsOff() throws JsonProcessingException {
        Data<byte[]> data = new Data<>(objectMapper.writeValueAsBytes(new Foo("bar")), "unknownType", 0, "application/json");
        List<DeserializingObject<byte[], Data<byte[]>>> result = serializer.deserialize(Stream.of(data), false)
                .collect(Collectors.toList());
        assertTrue(JsonNode.class.isAssignableFrom(result.get(0).getPayloadClass()));
        assertEquals(new ObjectNode(objectMapper.getNodeFactory(), singletonMap("foo", TextNode.valueOf("bar"))),
                     result.get(0).getPayload());
    }

    @Test
    void testConvertGetPayloadAs() throws JsonProcessingException {
        Foo foo = new Foo("bar");
        Data<byte[]> data = new Data<>(objectMapper.writeValueAsBytes(foo), "unknownType", 0, "application/json");
        List<DeserializingObject<byte[], Data<byte[]>>> result = serializer.deserialize(Stream.of(data), false)
                .collect(Collectors.toList());
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
        assertEquals(objectMapper.convertValue(input, Object.class), serializer.deserialize(serializer.serialize(input)));
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

    @Value
    static class ComplexObject {
        Child child;
        double dbl;

        @Value
        static class Child {
            String foo;
            int number;
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

    public static class RevisedObjectUpcaster {
        @Upcast(type = TYPE, revision = 0)
        public ObjectNode upcastFrom0(ObjectNode input) {
            return input.put("someInteger", 5);
        }

        @Upcast(type = TYPE, revision = 1)
        public ObjectNode upcastFrom1(ObjectNode input) {
            return input.put("name", input.remove("n").textValue());
        }

        @Upcast(type = TYPE, revision = 2)
        public Data<ObjectNode> upcastFrom2(Data<ObjectNode> input) {
            return new Data<>(input.getValue(), input.getType(), 3, "application/json");
        }
    }

    @Value
    private static class Foo {
        String foo;
    }

}