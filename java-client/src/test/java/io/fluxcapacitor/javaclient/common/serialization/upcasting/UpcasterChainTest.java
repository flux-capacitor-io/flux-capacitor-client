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

package io.fluxcapacitor.javaclient.common.serialization.upcasting;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpcasterChainTest {

    private final UpcasterStub upcasterStub = new UpcasterStub();
    private Upcaster<Data<String>> subject = UpcasterChain.createChain(Collections.singleton(upcasterStub), new StringConverter());

    @Test
    void testMappingPayload() {
        Data<String> input = new Data<>("input", "mapPayload", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(new Data<>(upcasterStub.mapPayload(input.getValue()), "mapPayload", 1, null)),
                result.collect(toList()));
    }

    @Test
    void testMappingData() {
        Data<String> input = new Data<>("input", "mapData", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(upcasterStub.mapData(input)), result.collect(toList()));
    }

    @Test
    void testDroppingPayload() {
        Data<String> input = new Data<>("input", "dropPayload", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    void testDroppingData() {
        Data<String> input = new Data<>("input", "dropData", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    void testNotOptionallyDroppingPayload() {
        Data<String> input = new Data<>("allowedPayload", "optionallyDropPayload", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(new Data<>(input.getValue(), input.getType(), 1, null)), result.collect(toList()));
    }

    @Test
    void testOptionallyDroppingPayload() {
        Data<String> input = new Data<>("forbiddenPayload", "optionallyDropPayload", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    void testNotOptionallyDroppingData() {
        Data<String> input = new Data<>("allowedPayload", "optionallyDropData", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(new Data<>(input.getValue(), input.getType(), 1, null)), result.collect(toList()));
    }

    @Test
    void testOptionallyDroppingData() {
        Data<String> input = new Data<>("forbiddenPayload", "optionallyDropData", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    void testSplittingData() {
        Data<String> input = new Data<>("input", "splitData", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(upcasterStub.splitData(input).collect(toList()), result.collect(toList()));
    }

    /*
        Chaining
     */

    @Test
    void testChaining() {
        Data<String> input = new Data<>("input", "chainStart", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(upcasterStub.chainEnd(input)), result.collect(toList()));
    }

    @Test
    void testChainingWithMultipleClasses() {
        NonConflictingUpcaster nonConflictingUpcaster = new NonConflictingUpcaster();
        subject = UpcasterChain.createChain(Arrays.asList(upcasterStub, nonConflictingUpcaster), new StringConverter());
        Data<String> input = new Data<>("input", "chainStart", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(nonConflictingUpcaster.chainEnd(input)), result.collect(toList()));
    }

    /*
        Unknown types/revisions
     */

    @Test
    void testNoUpcastingForUnknownType() {
        Data<String> input = new Data<>("input", "unknownType", 0, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(input), result.collect(toList()));
    }

    @Test
    void testNoUpcastingForUnknownRevision() {
        Data<String> input = new Data<>("input", "mapPayload", 1, null);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(input), result.collect(toList()));
    }

    /*
        Type conversion
     */

    @Test
    void testUpcastingWithTypeConversion() {
        Upcaster<SerializedObject<byte[], ?>> subject
                = UpcasterChain.create(Collections.singleton(upcasterStub), new StringConverter());
        Stream<SerializedObject<byte[], ?>> result =
                subject.upcast(Stream.of(new Data<>("input".getBytes(), "mapPayload", 0, null)));
        assertEquals(singletonList(new Data<>("mappedPayload".getBytes(), "mapPayload", 1, null)),
                result.map(SerializedObject::data).collect(toList()));
    }

    /*
        Failures
     */

    @Test
    void testExceptionForUpcasterWithUnexpectedDataType() {
        assertThrows(SerializationException.class, () -> UpcasterChain.createChain(Collections.singleton(upcasterStub), new NoConverter()));
    }

    @Test
    void testExceptionForConflictingUpcasters() {
        assertThrows(SerializationException.class, () -> UpcasterChain.createChain(Arrays.asList(upcasterStub, new ConflictingUpcaster()), new StringConverter()));
    }

    /*
        Lazy upcasting
     */

    @Test
    void testLazyUpcasting() {
        MonitoringUpcaster upcaster = new MonitoringUpcaster();
        Upcaster<Data<String>> subject =
                UpcasterChain.createChain(Collections.singletonList(upcaster), new StringConverter());
        Stream<Data<String>> resultStream = subject.upcast(Stream.of(new Data<>("foo", "upcastLazily", 0, null)));
        Data<String> result = resultStream.collect(toList()).get(0);
        assertFalse(upcaster.isInvoked());
        result.getValue();
        assertTrue(upcaster.isInvoked());
    }

    private static class UpcasterStub {

        @Upcast(type = "mapPayload", revision = 0)
        public String mapPayload(String input) {
            return "mappedPayload";
        }

        @Upcast(type = "mapData", revision = 0)
        public Data<String> mapData(Data<String> input) {
            return new Data<>("mappedPayload", input.getType(), input.getRevision() + 1, null);
        }

        @Upcast(type = "dropPayload", revision = 0)
        public void dropPayload(String input) {
        }

        @Upcast(type = "dropData", revision = 0)
        public void dropData() {
        }

        @Upcast(type = "optionallyDropPayload", revision = 0)
        public Optional<String> optionallyDropPayload(String input) {
            return Optional.of(input).filter(i -> i.equals("allowedPayload"));
        }

        @Upcast(type = "optionallyDropData", revision = 0)
        public Optional<Data<String>> optionallyDropData(Data<String> input) {
            return Optional.of(input).filter(i -> i.getValue().equals("allowedPayload"))
                    .map(i -> new Data<>(i.getValue(), i.getType(), 1, null));
        }

        @Upcast(type = "splitData", revision = 0)
        public Stream<Data<String>> splitData(Data<String> input) {
            return Stream.of(new Data<>(input.getValue(), input.getType(), input.getRevision() + 1, null),
                    new Data<>("someOtherValue", "someOtherType", 9, null));
        }

        @Upcast(type = "chainStart", revision = 0)
        public String chain_to1(String input) {
            return "chain0";
        }

        @Upcast(type = "chainStart", revision = 1)
        public String chain_to2(String input) {
            return "chain1";
        }

        @Upcast(type = "chainStart", revision = 2)
        public String chain_to3(String input) {
            return "chain2";
        }

        @Upcast(type = "chainStart", revision = 3)
        public Data<String> chain_to4(Data<String> input) {
            return new Data<>("chain3", "chainEnd", 0, null);
        }

        @Upcast(type = "chainEnd", revision = 0)
        public Data<String> chainEnd(Data<String> input) {
            return new Data<>("chainEnd", "chainEnd", 1, null);
        }

    }

    private static class NonConflictingUpcaster {
        @Upcast(type = "chainEnd", revision = 1)
        public Data<String> chainEnd(Data<String> input) {
            return new Data<>("chainEnd", "chainEnd", 2, null);
        }
    }

    private static class ConflictingUpcaster {
        @Upcast(type = "mapPayload", revision = 0)
        public String mapPayload(String input) {
            return "whatever";
        }
    }

    @Getter
    private static class MonitoringUpcaster {
        private boolean invoked;

        @Upcast(type = "upcastLazily", revision = 0)
        public String mapPayload(String input) {
            invoked = true;
            return "bar";
        }
    }

    private static class StringConverter implements Converter<String> {

        @Override
        public String convert(byte[] bytes) {
            return new String(bytes);
        }

        @Override
        public byte[] convertBack(String value) {
            return value.getBytes();
        }

        @Override
        public Class<String> getDataType() {
            return String.class;
        }
    }


    private static class NoConverter implements Converter<byte[]> {

        @Override
        public byte[] convert(byte[] bytes) {
            return bytes;
        }

        @Override
        public byte[] convertBack(byte[] bytes) {
            return bytes;
        }

        @Override
        public Class<byte[]> getDataType() {
            return byte[].class;
        }
    }


}