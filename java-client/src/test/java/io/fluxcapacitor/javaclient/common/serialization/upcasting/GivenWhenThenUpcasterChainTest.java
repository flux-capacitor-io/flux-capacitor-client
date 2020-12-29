package io.fluxcapacitor.javaclient.common.serialization.upcasting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fluxcapacitor.common.api.Data;
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
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.fluxcapacitor.javaclient.common.FileUtils.loadFile;
import static io.fluxcapacitor.javaclient.common.serialization.SerializationUtils.jsonMapper;
import static java.util.Collections.singletonList;

public class GivenWhenThenUpcasterChainTest {
    private static final String aggregateId = "test";

    static class WithJsonPatch {
        private final StreamingTestFixture testFixture = StreamingTestFixture.create(DefaultFluxCapacitor.builder().replaceSerializer(
                new JacksonSerializer(singletonList(new Upcaster()))), new Handler());


        @Test
        @SuppressWarnings("unchecked")
        void testUpcastingWithoutJsonPatch() {
            testFixture.givenDomainEvents(aggregateId, new CreateModelR0())
                    .whenQuery(new GetModel())
                    .expectResult(r -> ((Map<String, Object>) ((TestModel) r).events.get(0)).get("content").equals("patchedContent"));
        }


        @Test
        @SneakyThrows
        void testUpcastingWithDataInput() {
            testFixture.givenDomainEvents(aggregateId, new Data<>(
                    jsonMapper.readTree(loadFile(this.getClass(), "create-model-revision-0.json")),
                    "io.fluxcapacitor.javaclient.common.serialization.upcasting." +
                            "GivenWhenThenUpcasterChainTest$WithJsonPatch$CreateModel", 0))
                    .whenQuery(new GetModel())
                    .expectResult(new TestModel(singletonList(new CreateModel("patchedContent"))));
        }

//        @Test
//        void testUpcastingWithJsonPatch() {
//            testFixture.givenCommands(new UpcastedWithJsonPatch("someContent"))
//                    .whenQuery(new GetModel())
//                    .expectResult(new TestModel(singletonList(new UpcastedWithJsonPatch("patchedContent"))));
//        }

        @Value
        @Revision(1)
        public static class CreateModel {
            String content;
        }

        @Value
        public static class CreateModelR0 {
        }

        @Value
        @Revision(1)
        public static class UpcastedWithJsonPatch {
            String content;
        }

        @Value
        public static class GetModel {
        }


        public static class Upcaster {

            //This upcaster is a trick to get a CreateModel of revision 0 in the eventstore
            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting." +
                    "GivenWhenThenUpcasterChainTest$WithJsonPatch$CreateModelR0", revision = 0)
            @SneakyThrows
            public Data<JsonNode> castR0ToR1(Data<JsonNode> input) {
                return new Data<>(jsonMapper.readTree(loadFile(this.getClass(), "create-model-revision-0.json")),
                        "io.fluxcapacitor.javaclient.common.serialization.upcasting." +
                                "GivenWhenThenUpcasterChainTest$WithJsonPatch$CreateModel", 0);
            }

            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting." +
                    "GivenWhenThenUpcasterChainTest$WithJsonPatch$CreateModel", revision = 0)
            @SneakyThrows
            public ObjectNode upcastWithJsonNode(ObjectNode input) {
                return input.put("content", "patchedContent");
            }

//            @Upcast(type = "io.fluxcapacitor.javaclient.common.serialization.upcasting.GivenWhenThenUpcasterChainTest." +
//                    "WithJsonPatch.UpcastedWithJsonPatch", revision = 0)
//            @SneakyThrows
//            public JsonPatch upcastWithJsonPatch(ObjectNode input) {
//                return JsonPatch.fromJson(defaultObjectMapper.readTree(
//                        "{\"op\":\"replace\",\"path\":\"/content\",\"value\":\"patchedContent\"}"));
//            }
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
        public static class TestModel {
            List<Object> events;

            @ApplyEvent
            public static TestModel handle(CreateModel event) {
                return new TestModel(singletonList(event));
            }

            @ApplyEvent
            public static TestModel handle(UpcastedWithJsonPatch event) {
                return new TestModel(singletonList(event));
            }

        }

    }
}
