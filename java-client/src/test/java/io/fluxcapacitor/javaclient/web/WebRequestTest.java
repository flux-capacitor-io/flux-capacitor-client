package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import io.fluxcapacitor.javaclient.tracking.handling.HandleWebRequest;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import lombok.Builder;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static io.fluxcapacitor.javaclient.FluxCapacitor.*;
import static io.fluxcapacitor.javaclient.common.serialization.SerializationUtils.jsonMapper;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

public class WebRequestTest {

    private static final String aggregateId = "test";
    private final List<TestFixture> fixtures = Arrays.asList(TestFixture.create(new Handler()), TestFixture.createAsync(new Handler()));
    private static final UpsertModel upsertModel = new UpsertModel("something");

    @Test
    void handleGetReturnObject() {
        fixtures.forEach(f -> f.givenCommands(upsertModel)
                .whenWebRequest(new WebRequest(null, "/get", "GET"))
                .expectResult(TestModel.builder().payload("something").build())
                .expectWebResponse(new WebResponse(TestModel.builder().payload("something").build(), 200)));
    }

    @Test
    void handleGetReturnWebResponse() {
        fixtures.forEach(f -> f.givenCommands(upsertModel)
                .whenWebRequest(new WebRequest(null, "/webResponse", "GET"))
                .expectResult(TestModel.builder().payload("something").build())
                .expectWebResponse(new WebResponse(TestModel.builder().payload("something").build(), 201,
                        singletonMap("webResponse", singletonList("someValue")))));
    }

    @Test
    void handleTechnicalException() {
        fixtures.forEach(f -> f.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(null, "/technicalException", "GET"))
                .expectWebResponse(new WebResponse(singletonMap("error", "An unexpected error occurred"), 500)));
    }

    @Test
    void handleFunctionalException() {
        fixtures.forEach(f -> f.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(null, "/functionalException", "GET"))
                .expectWebResponse(new WebResponse(singletonMap("error", "Do you see me?"), 401)));
    }

    @Test
    void handleSpecificPost() {
        fixtures.forEach(f -> f.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(upsertModel, "/specific", "POST"))
                .expectResult(upsertModel)
                .expectCommands(upsertModel)
                .expectWebResponse(new WebResponse(upsertModel, 200)));
    }

    @Test
    void handleObjectPost() {
        fixtures.forEach(f -> f.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(upsertModel, "/object", "POST"))
                .expectResult(upsertModel)
                .expectCommands(upsertModel)
                .expectWebResponse(new WebResponse(upsertModel, 200)));
    }

    @Test
    @SneakyThrows
    void handleStringPost() {
        String expectedResult = jsonMapper.writeValueAsString(upsertModel);
        fixtures.forEach(f -> f.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(upsertModel, "/string", "POST"))
                .expectResult(expectedResult)
                .expectCommands(upsertModel)
                .expectWebResponse(new WebResponse(expectedResult, 200)));
    }

    @Test
    @SneakyThrows
    void handleBytesPost() {
        byte[] expectedResult = jsonMapper.writeValueAsBytes(upsertModel);
        TestFixture.createAsync(new Handler()).givenNoPriorActivity()
                .whenWebRequest(new WebRequest(upsertModel, "/bytes", "POST"))
                .expectResult(expectedResult)
                .expectCommands(upsertModel)
                .expectWebResponse(new WebResponse(expectedResult, 200));
    }

    @Test
    @SneakyThrows
    void handleJsonPost() {
        JsonNode expectedResult = jsonMapper.convertValue(upsertModel, JsonNode.class);
        fixtures.forEach(f -> f.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(upsertModel, "/json", "POST"))
                .expectResult(expectedResult)
                .expectCommands(upsertModel)
                .expectWebResponse(new WebResponse(expectedResult, 200)));
    }


    private static class Handler {

        @HandleWebRequest(path = "/get", method = "GET")
        @SneakyThrows
        Object handleGet() {
            return queryAndWait(new GetModel());
        }

        @HandleWebRequest(path = "/webResponse", method = "GET")
        @SneakyThrows
        Object handleGetWebRequest(WebRequest webRequest) {
            return new WebResponse(queryAndWait(new GetModel()), 201, singletonMap("webResponse", singletonList("someValue")));
        }

        @HandleWebRequest(path = "/technicalException", method = "GET")
        @SneakyThrows
        void handleTechnicalException() {
            throw new RuntimeException("Do you see me?");
        }

        @HandleWebRequest(path = "/functionalException", method = "GET")
        @SneakyThrows
        void handleFunctionalException() {
            throw new UnauthenticatedException("Do you see me?");
        }

        @HandleWebRequest(path = "/bytes", method = "POST")
        @SneakyThrows
        byte[]  handleBytePost(byte[] payload) {
            sendCommandAndWait(jsonMapper.readValue(payload, UpsertModel.class));
            return payload;
        }

        @HandleWebRequest(path = "/object", method = "POST")
        @SneakyThrows
        Object handleObjectPost(Object payload) {
            sendCommandAndWait(jsonMapper.convertValue(payload, UpsertModel.class));
            return payload;
        }

        @HandleWebRequest(path = "/string", method = "POST")
        @SneakyThrows
        String handleStringPost(String payload) {
            sendCommandAndWait(jsonMapper.readValue(payload, UpsertModel.class));
            return payload;
        }

        @HandleWebRequest(path = "/specific", method = "POST")
        UpsertModel handleSpecificPost(UpsertModel payload, Metadata metadata) {
            sendCommandAndWait(payload);
            return payload;
        }

        @HandleWebRequest(path = "/json", method = "POST")
        @SneakyThrows
        JsonNode handleJsonPost(JsonNode payload, Metadata metadata) {
            sendAndForgetCommand(jsonMapper.convertValue(payload, UpsertModel.class));
            return payload;
        }

        @HandleCommand
        void handle(UpsertModel command, Metadata metadata) {
            loadAggregate(aggregateId, TestModel.class).assertLegal(command).apply(command, metadata);
        }

        @HandleQuery
        TestModel handle(GetModel query) {
            return loadAggregate(aggregateId, TestModel.class).get();
        }

    }

    @Value
    public static class GetModel {
    }


    @Value
    public static class UpsertModel {
        String payload;
    }

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    public static class TestModel {
        @Singular
        List<String> payloads;

        @ApplyEvent
        public static TestModel create(UpsertModel event) {
            return new TestModel(singletonList(event.getPayload()));
        }
    }

}
