/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Builder;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.fluxcapacitor.javaclient.common.serialization.SerializationUtils.deserialize;
import static io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer.defaultObjectMapper;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class GivenWhenThenUpcasterChainTest {
    private static final String aggregateId = "test";

    static class WithJsonNode {
        private final StreamingTestFixture testFixture = StreamingTestFixture.create(
                DefaultFluxCapacitor.builder().replaceSerializer(
                        new JacksonSerializer(singletonList(new JsonNodeUpcaster()))), new Handler());

        @Test
        void testUpcastingWithDataInput() {
            testFixture.givenDomainEvents(aggregateId, deserialize(this.getClass(), "create-model-revision-0.json"))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(singletonList(new CreateModel("patchedContent"))));
        }

        public static class JsonNodeUpcaster {
            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest$CreateModel",
                    revision = 0)
            public ObjectNode upcast(ObjectNode input) {
                return input.put("content", "patchedContent");
            }
        }
    }

    static class WithJsonPatch {
        private final StreamingTestFixture testFixture = StreamingTestFixture.create(
                DefaultFluxCapacitor.builder().replaceSerializer(
                        new JacksonSerializer(singletonList(new JsonPatchUpcaster()))), new Handler());

        @Test
        void testUpcastingWithDataInput() {
            testFixture.givenDomainEvents(aggregateId, deserialize(this.getClass(), "create-model-revision-0.json"))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(singletonList(new CreateModel("patchedContent"))));
        }

        public static class JsonPatchUpcaster {

            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest$CreateModel",
                    revision = 0)
            @SneakyThrows
            public JsonPatch upcast(ObjectNode input) {
                return JsonPatch.fromJson(defaultObjectMapper.readTree(
                        "[{\"op\":\"replace\",\"path\":\"/content\",\"value\":\"patchedContent\"}]"));
            }
        }
    }

    static class WithoutInputParam {
        private final StreamingTestFixture testFixture = StreamingTestFixture.create(
                DefaultFluxCapacitor.builder().replaceSerializer(
                        new JacksonSerializer(singletonList(new JsonPatchUpcasterWithoutInputParam()))), new Handler());

        @Test
        void testUpcastingWithDataInput() {
            testFixture.givenDomainEvents(aggregateId, deserialize(this.getClass(), "create-model-revision-0.json"))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(singletonList(new CreateModel("patchedContent"))));
        }

        public static class JsonPatchUpcasterWithoutInputParam {

            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest$CreateModel",
                    revision = 0)
            @SneakyThrows
            public JsonPatch upcast() {
                return JsonPatch.fromJson(defaultObjectMapper.readTree(
                        "[{\"op\":\"replace\",\"path\":\"/content\",\"value\":\"patchedContent\"}]"));
            }
        }
    }

    static class WithChain {
        private final StreamingTestFixture testFixture = StreamingTestFixture.create(
                DefaultFluxCapacitor.builder().replaceSerializer(
                        new JacksonSerializer(singletonList(new JsonPatchUpcaster()))), new Handler());

        @Test
        void testUpcastingWithDataInput() {
            testFixture.givenDomainEvents(aggregateId,
                    deserialize(this.getClass(), "create-model-revision-0.json"),
                    deserialize(this.getClass(), "update-model-revision-0.json"))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(asList(new CreateModel("someContent"),
                            new UpdateModel("patchedOneAgain", "patchedTwo"))));
        }


        public static class JsonPatchUpcaster {

            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest$UpdateModel",
                    revision = 0)
            @SneakyThrows
            public JsonPatch upcast0() {
                return JsonPatch.fromJson(defaultObjectMapper.readTree(
                        "[{\"op\":\"replace\",\"path\":\"/one\",\"value\":\"patchedOne\"}]"));
            }

            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest$UpdateModel",
                    revision = 1)
            @SneakyThrows
            public JsonPatch upcast1() {
                return JsonPatch.fromJson(defaultObjectMapper.readTree(
                        "[{\"op\":\"replace\",\"path\":\"/two\",\"value\":\"patchedTwo\"}]"));
            }

            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest$UpdateModel",
                    revision = 2)
            @SneakyThrows
            public JsonPatch upcast2() {
                return JsonPatch.fromJson(defaultObjectMapper.readTree(
                        "[{\"op\":\"replace\",\"path\":\"/one\",\"value\":\"patchedOneAgain\"}]"));
            }
        }

    }


    @Value
    @Revision(1)
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public static class CreateModel {
        String content;
    }

    @Value
    @Revision(3)
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    public static class UpdateModel {
        String one, two;
    }

    @Value
    public static class GetModel {
    }

    private static class Handler {
        @HandleCommand
        void handle(Object command, Metadata metadata) {
            FluxCapacitor.loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
        }

        @HandleQuery
        TestModel handle(GetModel query) {
            return FluxCapacitor.loadAggregate(aggregateId, TestModel.class).get();
        }

    }

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    public static class TestModel {
        @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
        @Singular
        List<Object> events;

        @ApplyEvent
        public static TestModel handle(CreateModel event) {
            return new TestModel(singletonList(event));
        }

        @ApplyEvent
        public TestModel handle(UpdateModel event) {
            return toBuilder().event(event).build();
        }
    }
}
