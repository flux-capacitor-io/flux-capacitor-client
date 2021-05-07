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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.FileUtils;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.common.serialization.JsonUtils;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonInverterTest {

    private final JacksonSerializer subject = new JacksonSerializer();

    @Test
    void testMixedObject() throws Exception {
        String json = FileUtils.loadFile(JacksonInverterTest.class, "mixed-object.json");
        MixedObject value = new ObjectMapper().readValue(json, MixedObject.class);
        testReversion(value);
    }

    @Test
    void testMap() {
        Map<Object, Object> map = new HashMap<>();
        map.put("foo", UUID.randomUUID().toString());
        map.put("1", UUID.randomUUID().toString());
        Number sameNumber = new BigDecimal(10);
        map.put("bar", sameNumber);
        map.put("sameBar", sameNumber);
        map.computeIfAbsent("otherMap", k -> {
            Map<Object, Object> otherMap = new HashMap<>();
            otherMap.put("other", false);
            return otherMap;
        });
        map.computeIfAbsent("collection", k -> {
            List<Object> collection = new ArrayList<>();
            collection.add("døg");
            collection.add("cát");
            collection.add(null);
            collection.add(emptyMap());
            Map<Object, Object> mapInCollection = new HashMap<>();
            mapInCollection.put("mapKey1", false);
            mapInCollection.put("mapKey2", "hi");
            mapInCollection.put("mapKey3", new ArrayList<>());
            collection.add(mapInCollection);
            return collection;
        });
        testReversion(map);
    }

    @Test
    void testNull() {
        testReversion(null);
    }

    @Test
    void testString() {
        testReversion("foo");
    }

    @Test
    void testNumber() {
        testReversion(new BigDecimal("100.100"));
    }

    @Test
    void testBoolean() {
        testReversion(true);
    }

    @Test
    void testArray() {
        testReversion(new Object[]{"1", "two", new BigDecimal("3")});
    }

    @Test
    void testEmptyArray() {
        testReversion(new Object[0]);
    }

    @Test
    void testCollection() {
        testReversion(Arrays.asList("1", "two", new BigDecimal("3")));
    }

    @Test
    void testEmptyCollection() {
        testReversion(Collections.emptyList());
    }

    @Test
    void testDocumentSerializationViaJackson() {
        Object value = JsonUtils.fromFile(JacksonInverterTest.class, "mixed-object.json", MixedObject.class);
        Document document = subject.toDocument(value, "test", "test", Instant.now(), Instant.now());
        String json = JsonUtils.asPrettyJson(document);

    }

    private void testReversion(Object value) {
        Document document = subject.toDocument(value, "test", "test", Instant.now(), Instant.now());
        Object result = subject.fromDocument(document);
        if (value != null && value.getClass().isArray()) {
            if (!Objects.deepEquals(value, result)) {
                assertArrayEquals((Object[]) value, (Object[]) result);
            }
        } else {
            assertEquals(value, result);
        }
    }

    @Value
    private static class MixedObject {
        CollectionsObject collections;
        NumbersObject numbers;
        String string;
        boolean bool;
        Object nil;
        String[] strings;
        Map<?, ?> emptyMap;
        Set<?> emptySet;
        byte[] bytes;

        @Value
        private static class CollectionsObject {
            Map<String, NumbersObject> objectMap;
            Map<Integer, Integer> integerMap;
            List<Integer> integerList;
            List<String> stringList;
        }

        @Value
        private static class NumbersObject {
            byte b;
            short s;
            int i;
            long l;
            float f;
            double d;
            BigInteger bigInt;
            BigDecimal bigDec;
        }
    }
}