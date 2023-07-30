/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.javaclient.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.GivenUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.GET;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.POST;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_HANDSHAKE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_MESSAGE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_OPEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

public class HandleWebTest {

    @Nested
    class GenericTests {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("/get").build()).expectResult("get");
        }

        @Test
        void testGetFullUrl() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("http://localhost:8080/get").build())
                    .expectResult("get8080");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/string").payload("payload").build())
                    .expectResult("payload");
        }

        @Test
        void testPostBytes() {
            testFixture.whenWebRequest(
                    WebRequest.builder().method(POST).url("/bytes").payload("payload".getBytes()).build())
                    .expectResult("payload".getBytes());
        }

        @Test
        void testPostObject() {
            var object = Map.of("foo", "bar");
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/object")
                                               .payload(object).build()).expectResult(object);
        }

        @Test
        void testPostPayload() {
            var object = new Payload();
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/payload")
                                               .payload(object).build()).expectResult(object);
        }

        @Test
        void testPostJson() {
            var object = Map.of("foo", "bar");
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/json")
                                               .payload(object).build())
                    .expectResult((JsonNode) JsonUtils.valueToTree(object));
        }

        @Test
        void testWithoutSlash() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("get").build()).expectResult("get");
        }

        @Test
        void testWrongPath() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("/unknown").build())
                    .expectExceptionalResult(TimeoutException.class);
        }

        @Test
        void testPostPayloadRequiringUser_validWithUser() {
            PayloadRequiringUser object = new PayloadRequiringUser();
            TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(
                            new GivenUserProvider(new MockUser())), new Handler())
                    .whenWebRequest(WebRequest.builder().method(POST).url("/requiresUser").payload(object).build())
                    .expectResult(object);
        }

        @Test
        void testPostPayloadRequiringUser_invalidWithoutUser() {
            TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(
                    new GivenUserProvider(null)), new Handler())
                    .whenWebRequest(WebRequest.builder().method(POST).url("/requiresUser")
                                            .payload(new PayloadRequiringUser()).build())
                    .expectExceptionalResult(UnauthenticatedException.class);
        }

        private class Handler {
            @HandleWeb(value = "/get", method = GET)
            String get() {
                return "get";
            }

            @HandleWeb(value = "http://localhost:8080/get", method = GET)
            String getWithProtocol() {
                return "get8080";
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

            @HandleWeb(value = "/payload", method = POST)
            Object post(Payload body) {
                return body;
            }

            @HandleWeb(value = "/requiresUser", method = POST)
            Object post(PayloadRequiringUser body) {
                return body;
            }

            @HandleWeb(value = "/json", method = POST)
            Object post(JsonNode body) {
                return body;
            }
        }
    }

    @Value
    static class Payload {
    }

    @RequiresUser
    @Value
    static class PayloadRequiringUser {
    }

    @Nested
    class AnnotationOverrides {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("/get").build()).expectResult("get");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/string").payload("payload").build())
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

    @Nested
    class WebSocketTests {
        private final TestFixture testFixture = TestFixture.createAsync(new Handler())
                .resultTimeout(Duration.ofSeconds(1)).consumerTimeout(Duration.ofSeconds(1));

        @Test
        void testAutoHandshake() {
            testFixture.whenWebRequest(WebRequest.builder().method(WS_HANDSHAKE).url("/auto").build())
                    .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).send(
                            any(), any(SerializedMessage.class)))
                    .expectNoErrors();
        }

        @Test
        void testOpen() {
            testFixture.whenWebRequest(WebRequest.builder().method(WS_OPEN).url("/auto").build()
                                               .addMetadata("sessionId", "someSession"))
                    .expectWebResponses(WebResponse.builder().payload("open").build()
                                                .addMetadata("sessionId", "someSession"));
        }

        @Test
        void testResponse() {
            testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/response").build())
                    .expectWebResponses(WebResponse.builder().payload("response").build());
        }

        @Test
        void testNoResponse() {
            testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/noResponse").build())
                    .expectNoWebResponses();
        }

        @Test
        void testViaSession() {
            testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/viaSession").build()
                                               .addMetadata("sessionId", "someSession"))
                    .expectWebResponses("viaSession");
        }

        private class Handler {
            @HandleSocketHandshake("manual")
            String handshake() {
                return "handshake";
            }

            @HandleSocketOpen("auto")
            String open() {
                return "open";
            }

            @HandleSocketMessage("response")
            String response() {
                return "response";
            }

            @HandleSocketMessage("noResponse")
            void noResponse() {
            }

            @HandleSocketMessage("viaSession")
            @SneakyThrows
            void viaSession(SocketSession session) {
                session.sendMessage("viaSession");
            }
        }
    }
}
