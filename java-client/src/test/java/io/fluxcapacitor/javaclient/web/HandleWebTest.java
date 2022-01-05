package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.GET;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.POST;

public class HandleWebTest {

    @Nested
    class GenericTests {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).path("/get").build()).expectResult("get");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).path("/string").payload("payload").build())
                    .expectResult("payload");
        }

        @Test
        void testPostBytes() {
            testFixture.whenWebRequest(
                    WebRequest.builder().method(POST).path("/bytes").payload("payload".getBytes()).build())
                    .expectResult("payload".getBytes());
        }

        @Test
        void testPostObject() {
            var object = Map.of("foo", "bar");
            testFixture.whenWebRequest(WebRequest.builder().method(POST).path("/object")
                                               .payload(object).build()).expectResult(object);
        }

        @Test
        void testPostJson() {
            var object = Map.of("foo", "bar");
            testFixture.whenWebRequest(WebRequest.builder().method(POST).path("/json")
                                               .payload(object).build())
                    .expectResult((JsonNode) JsonUtils.valueToTree(object));
        }

        @Test
        void testWithoutSlash() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).path("get").build()).expectResult("get");
        }

        @Test
        void testWrongPath() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).path("/unknown").build())
                    .expectException(TimeoutException.class);
        }

        private class Handler {
            @HandleWeb(value = "/get", method = GET)
            String get() {
                return "get";
            }

            @HandleWeb(value = "/string", method = POST)
            String post(String body) {
                return body;
            }

            @HandleWeb(value = "/bytes", method = POST)
            byte[] post(byte[] body) {
                return body;
            }

            @HandleWeb(value = "/object", method = POST)
            Object post(Object body) {
                return body;
            }

            @HandleWeb(value = "/json", method = POST)
            Object post(JsonNode body) {
                return body;
            }
        }
    }

    @Nested
    class AnnotationOverrides {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).path("/get").build()).expectResult("get");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).path("/string").payload("payload").build())
                    .expectResult("payload");
        }

        private class Handler {
            @HandleGet("get")
            String get() {
                return "get";
            }

            @HandlePost("/string")
            String post(String body) {
                return body;
            }
        }
    }
}
