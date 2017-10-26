/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Revision;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcast;
import lombok.Value;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

public class JacksonSerializerTest {
    private static final String TYPE =
            "io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializerTest$RevisedObject";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JacksonSerializer subject = new JacksonSerializer(Collections.singletonList(new RevisedObjectUpcaster()));

    @Test
    public void testSerialization() {
        Data<byte[]> data = subject.serialize(new RevisedObject("test", 42));
        assertEquals(TYPE, data.getType());
        assertEquals(2, data.getRevision());
    }

    @Test
    public void testDeserializeWithoutUpcasting() {
        RevisedObject testObject = new RevisedObject("test", 42);
        assertEquals(testObject, subject.deserialize(subject.serialize(testObject)));
    }

    @Test
    public void testDeserializeWithUpcasting() throws JsonProcessingException {
        RevisedObject expected = new RevisedObject("test", 5);
        assertEquals(expected, subject.deserialize(createRev0Data("test")));
    }

    @Test
    public void testDeserializeStream() throws JsonProcessingException {
        List<RevisedObject> expected = asList(new RevisedObject("test0", 5), new RevisedObject("test2", 42));
        List<?> actual = subject.deserialize(Stream.of(createRev0Data(expected.get(0).getName()), subject.serialize(expected.get(1)))).collect(
                Collectors.toList());
        assertEquals(expected, actual);
    }

    private Data<byte[]> createRev0Data(String name) throws JsonProcessingException {
        ObjectNode rev0Payload = new ObjectNode(objectMapper.getNodeFactory());
        rev0Payload.put("n", name);
        return new Data<>(objectMapper.writeValueAsBytes(rev0Payload), TYPE, 0);
    }

    @Revision(2)
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
    }

}