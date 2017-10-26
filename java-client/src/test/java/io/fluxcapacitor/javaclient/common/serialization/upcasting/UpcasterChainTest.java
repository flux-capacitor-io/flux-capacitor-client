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

package io.fluxcapacitor.javaclient.common.serialization.upcasting;

import io.fluxcapacitor.common.api.Data;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

public class UpcasterChainTest {

    private UpcasterStub upcasterStub = new UpcasterStub();
    private Upcaster<Data<String>> subject = UpcasterChain.create(Collections.singleton(upcasterStub), String.class);

    @Test
    public void testMappingPayload() {
        Data<String> input = new Data<>("input", "mapPayload", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(new Data<>(upcasterStub.mapPayload(input.getValue()), "mapPayload", 1)),
                     result.collect(toList()));
    }

    @Test
    public void testMappingData() {
        Data<String> input = new Data<>("input", "mapData", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(upcasterStub.mapData(input)), result.collect(toList()));
    }

    @Test
    public void testDroppingPayload() {
        Data<String> input = new Data<>("input", "dropPayload", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    public void testDroppingData() {
        Data<String> input = new Data<>("input", "dropData", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    public void testNotOptionallyDroppingPayload() {
        Data<String> input = new Data<>("allowedPayload", "optionallyDropPayload", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(new Data<>(input.getValue(), input.getType(), 1)), result.collect(toList()));
    }

    @Test
    public void testOptionallyDroppingPayload() {
        Data<String> input = new Data<>("forbiddenPayload", "optionallyDropPayload", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    public void testNotOptionallyDroppingData() {
        Data<String> input = new Data<>("allowedPayload", "optionallyDropData", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(new Data<>(input.getValue(), input.getType(), 1)), result.collect(toList()));
    }

    @Test
    public void testOptionallyDroppingData() {
        Data<String> input = new Data<>("forbiddenPayload", "optionallyDropData", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(emptyList(), result.collect(toList()));
    }

    @Test
    public void testSplittingData() {
        Data<String> input = new Data<>("input", "splitData", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(upcasterStub.splitData(input).collect(toList()), result.collect(toList()));
    }

    /*
        Chaining
     */

    @Test
    public void testChaining() {
        Data<String> input = new Data<>("input", "chainStart", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(upcasterStub.chainEnd(input)), result.collect(toList()));
    }

    @Test
    public void testChainingWithMultipleClasses() {
        NonConflictingUpcaster nonConflictingUpcaster = new NonConflictingUpcaster();
        subject = UpcasterChain.create(Arrays.asList(upcasterStub, nonConflictingUpcaster), String.class);
        Data<String> input = new Data<>("input", "chainStart", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(nonConflictingUpcaster.chainEnd(input)), result.collect(toList()));
    }

    /*
        Unknown types/revisions
     */

    @Test
    public void testNoUpcastingForUnknownType() {
        Data<String> input = new Data<>("input", "unknownType", 0);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(input), result.collect(toList()));
    }

    @Test
    public void testNoUpcastingForUnknownRevision() {
        Data<String> input = new Data<>("input", "mapPayload", 1);
        Stream<Data<String>> result = subject.upcast(Stream.of(input));
        assertEquals(singletonList(input), result.collect(toList()));
    }

    /*
        Failures
     */

    @Test(expected = IllegalArgumentException.class)
    public void testExceptionForUpcasterWithUnexpectedDataType() {
        UpcasterChain.create(Collections.singleton(upcasterStub), byte[].class);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExceptionForConflictingUpcasters() {
        UpcasterChain.create(Arrays.asList(upcasterStub, new ConflictingUpcaster()), String.class);
    }

    private static class UpcasterStub {

        @Upcast(type = "mapPayload", revision = 0)
        public String mapPayload(String input) {
            return "mappedPayload";
        }

        @Upcast(type = "mapData", revision = 0)
        public Data<String> mapData(Data<String> input) {
            return new Data<>("mappedPayload", input.getType(), input.getRevision() + 1);
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
                    .map(i -> new Data<>(i.getValue(), i.getType(), 1));
        }

        @Upcast(type = "splitData", revision = 0)
        public Stream<Data<String>> splitData(Data<String> input) {
            return Stream.of(new Data<>(input.getValue(), input.getType(), input.getRevision() + 1),
                             new Data<>("someOtherValue", "someOtherType", 9));
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
            return new Data<>("chain3", "chainEnd", 0);
        }

        @Upcast(type = "chainEnd", revision = 0)
        public Data<String> chainEnd(Data<String> input) {
            return new Data<>("chainEnd", "chainEnd", 1);
        }

    }

    private static class NonConflictingUpcaster {
        @Upcast(type = "chainEnd", revision = 1)
        public Data<String> chainEnd(Data<String> input) {
            return new Data<>("chainEnd", "chainEnd", 2);
        }
    }

    private static class ConflictingUpcaster {
        @Upcast(type = "mapPayload", revision = 0)
        public String mapPayload(String input) {
            return "whatever";
        }
    }

}