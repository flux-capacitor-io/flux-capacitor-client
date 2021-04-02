package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleQuery;
import lombok.Builder;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static io.fluxcapacitor.javaclient.common.serialization.SerializationUtils.jsonMapper;
import static io.fluxcapacitor.javaclient.web.WebTestUtils.createWebServerTestFixture;
import static io.undertow.Handlers.path;
import static io.undertow.util.Headers.CONTENT_TYPE;
import static java.util.Collections.singletonList;

@Slf4j
public class WebServerTest {

    private static final String aggregateId = "test";
    private static final UpsertModel upsertModel = new UpsertModel("something");

    private final TestFixture testFixture = createWebServerTestFixture(path()
            .addPrefixPath("/getJson", exchange -> {
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json");
                exchange.setStatusCode(200);
                exchange.getResponseSender().send("{\"payload\":\"something\"}");
            }).addPrefixPath("/getVoid", exchange -> {
                exchange.getResponseHeaders().put(CONTENT_TYPE, "application/json");
                exchange.setStatusCode(204);
                exchange.endExchange();
            }).addPrefixPath("/getText", exchange -> {
                exchange.getResponseHeaders().put(CONTENT_TYPE, "text/plain");
                exchange.setStatusCode(200);
                exchange.getResponseSender().send("Do you see me?");
            }).addPrefixPath("/getPostedPayloadBack", e -> {
                e.dispatch(() -> e.getRequestReceiver().receiveFullBytes((exchange, message) -> {
                    exchange.getResponseSender().send(ByteBuffer.wrap(message));
                }));
            }), new Handler());

    @Test
    @SneakyThrows
    void testGetJsonFromServer() {
        byte[] expectedResult = jsonMapper.writeValueAsBytes(upsertModel);
        testFixture.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(null, "/getJson", "GET"))
                .expectResult(expectedResult)
                .expectWebResponse(new WebResponse(expectedResult, 200));
    }

    @Test
    @SneakyThrows
    void testGetVoidFromServer() {
        testFixture.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(null, "/getVoid", "GET"))
                .expectResult(null)
                .expectWebResponse(new WebResponse(null, 204));
    }

    @Test
    @SneakyThrows
    void testGetTextFromServer() {
        testFixture.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(null, "/getText", "GET"))
                .expectResult("Do you see me?".getBytes(StandardCharsets.UTF_8))
                .expectWebResponse(new WebResponse("Do you see me?".getBytes(StandardCharsets.UTF_8), 200));
    }

    @Test
    @SneakyThrows
    void testGetInputBackFromServer() {
        byte[] expectedResult = jsonMapper.writeValueAsBytes(upsertModel);
        testFixture.givenNoPriorActivity()
                .whenWebRequest(new WebRequest(upsertModel, "/getPostedPayloadBack", "POST"))
                .expectResult(expectedResult)
                .expectWebResponse(new WebResponse(expectedResult, 200));
    }

    private static class Handler {
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
