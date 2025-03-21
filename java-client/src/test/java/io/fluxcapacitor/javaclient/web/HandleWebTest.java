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
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.FixedUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.web.path.ClassPathHandler;
import io.fluxcapacitor.javaclient.web.path.PackagePathHandler;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.HttpCookie;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.GET;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.POST;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.PUT;
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
        void testGetViaPath() {
            testFixture.whenGet("/getViaPath").expectResult("getViaPath");
        }

        @Test
        void testGet_shortHand() {
            testFixture.whenGet("/get").expectResult("get");
        }

        @Test
        void testGet_disabled() {
            testFixture.whenGet("/disabled").expectExceptionalResult(TimeoutException.class);
        }

        @Test
        void testGetFullUrl() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("http://localhost:8080/get").build())
                    .expectResult("get8080");
        }

        @Test
        void testGetFullUrl_other() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("http://localhost:8080/other").build())
                    .expectResult("other8080");
        }

        @Test
        void testGetFullUrl_otherPort() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("http://localhost:8081/get").build())
                    .expectResult("get8081");
        }

        @Test
        void testPostString() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/string").payload("payload").build())
                    .expectResult("payload")
                    .<WebResponse>expectResultMessage(r -> r.getStatus() == 200);
        }

        @Test
        void testPostWithoutResult() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/noResult").payload("payload").build())
                    .expectNoResult()
                    .<WebResponse>expectResultMessage(r -> r.getStatus() == 204);
        }

        @Test
        void testPostString_shortHand() {
            testFixture.whenPost("/string", "payload")
                    .expectResult("payload")
                    .<WebResponse>expectResultMessage(r -> r.getStatus() == 200);
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
        void testPostPayloadAsString() {
            var object = new Payload();
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/payload")
                                               .payload(JsonUtils.asPrettyJson(object)).build()).expectResult(object);
        }

        @Test
        void testPostJson() {
            var object = Map.of("foo", "bar");
            testFixture.whenWebRequest(
                            WebRequest.builder().method(POST).url("/json").payload(object).build())
                    .expectResult(JsonUtils.<JsonNode>valueToTree(object))
                    .expectResultMessage(r -> r.getPayloadAs(Map.class).equals(object));
        }

        @Test
        void testPostJsonFromFile() {
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/json").payload("/web/body.json").build())
                    .expectResult("/web/body.json");
        }

        @Test
        void testPostJsonFromFile_given() {
            testFixture.givenWebRequest(
                            WebRequest.builder().method(POST).url("/json").payload("/web/body.json").build())
                    .whenNothingHappens().expectNoErrors();
        }

        @Test
        void testPostJsonFromFile_given_shortHand() {
            testFixture.givenPost("/json", "/web/body.json")
                    .whenNothingHappens().expectNoErrors();
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
                            new FixedUserProvider(new MockUser())), new Handler())
                    .whenWebRequest(WebRequest.builder().method(POST).url("/requiresUser").payload(object).build())
                    .expectResult(object);
        }

        @Test
        void testPostPayloadRequiringUser_invalidWithoutUser() {
            TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(
                            new FixedUserProvider(() -> null)), new Handler())
                    .whenWebRequest(WebRequest.builder().method(POST).url("/requiresUser")
                                            .payload(new PayloadRequiringUser()).build())
                    .expectExceptionalResult(UnauthenticatedException.class);
        }

        @Test
        void testExpectWebRequestEmptyPayload() {
            TestFixture.create(new Object() {
                @HandlePost("/foo")
                void foo() {}
            }).whenApplying(fc -> fc.webRequestGateway().sendAndWait(
                    WebRequest.builder().method(POST).url("/foo").build()))
                    .expectWebRequest(r -> r.getMethod() == POST);

        }

        private class Handler {
            @Path("/getViaPath")
            @HandleWeb(method = GET)
            String getViaPath() {
                return "getViaPath";
            }

            @HandleWeb(value = "/get", method = GET)
            String get() {
                return "get";
            }

            @HandleWeb(value = "/disabled", method = GET, disabled = true)
            String disabledGet() {
                return "get";
            }

            @HandleWeb(value = "http://localhost:8080/get", method = GET)
            String get_8080() {
                return "get8080";
            }

            @HandleWeb(value = "http://localhost:8080/other", method = GET)
            String getOther_8080() {
                return "other8080";
            }

            @HandleWeb(value = "http://localhost:8081/get", method = GET)
            String get_8081() {
                return "get8081";
            }

            @HandleWeb(value = "/string", method = POST)
            String post(String body) {
                return body;
            }

            @HandleWeb(value = "/noResult", method = POST)
            void postWithoutResult(String body) {
                //no op
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
    static class JsonResponse {
        String userId;
    }

    @Value
    static class Payload {
    }

    @RequiresUser
    @Value
    static class PayloadRequiringUser {
    }

    @Nested
    class PathTests {

        final TestFixture testFixture = TestFixture.create(new ClassPathHandler(), new PackagePathHandler());

        @Test
        void classPathTest() {
            testFixture.whenGet("/class/get").expectResult("get");
        }

        @Test
        void packagePathTest() {
            testFixture.whenGet("/package/get").expectResult("get");
        }

        @Test
        void methodPathTest() {
            testFixture.whenGet("/method/get").expectResult("get");
        }
    }

    @Nested
    class AnnotationOverrides {
        private final TestFixture testFixture = TestFixture.create(new Handler());

        @Test
        void testGet() {
            testFixture.whenWebRequest(WebRequest.builder().method(GET).url("/get").build()).expectResult("get");
        }

        @Test
        void testPutOrPost() {
            testFixture
                    .whenWebRequest(WebRequest.builder().method(POST).url("/string").payload("payload").build())
                    .expectResult("payload")
                    .andThen()
                    .whenPut("/string", "payload")
                    .expectResult("payload")
                    .andThen()
                    .whenPost("/string", "payload")
                    .expectResult("payload");
        }

        @Test
        void testPutOrPost2() {
            testFixture
                    .whenPost("/string2", "payload")
                    .expectResult("payload")
                    .andThen()
                    .whenPut("/string2", "payload")
                    .expectResult("payload")
                    .andThen()
                    .whenPost("/string2", "payload")
                    .expectResult("payload");
        }

        private static class Handler {
            @HandleGet("get")
            String get() {
                return "get";
            }

            @HandleWeb(value = "/string", method = {PUT, POST})
            String putOrPost(String body) {
                return body;
            }

            @HandlePost("/string2")
            @HandlePut("/string2")
            String putOrPost2(String body) {
                return body;
            }
        }
    }

    @Nested
    class RestTests {

        @Nested
        class PathParamTests {

            final TestFixture testFixture = TestFixture.create(new Handler());

            @Test
            void testPathParam_String() {
                testFixture.whenGet("/string/123").expectResult("123");
            }

            @Test
            void testPathParam_Number() {
                testFixture.whenGet("/number/123").expectResult(123);
            }

            @Test
            void testPathParam_Non_Number_Fails() {
                testFixture.whenGet("/number/123a").expectExceptionalResult(TimeoutException.class);
            }

            @Test
            void testPathParam_Id() {
                testFixture.whenGet("/id/123").expectResult(new SomeId("123"));
            }

            static class Handler {
                @HandleGet("string/{foo}")
                Object get(@PathParam String foo) {
                    return foo;
                }

                @HandleGet("number/{foo:[0-9]+}")
                Object get(@PathParam int foo) {
                    return foo;
                }

                @HandleGet("id/{foo}")
                Object get(@PathParam SomeId foo) {
                    return foo;
                }
            }
        }

        @Nested
        class QueryParamTests {

            final TestFixture testFixture = TestFixture.create(new Handler());

            @Test
            void testPathParam_int() {
                testFixture.whenGet("/?offset=1").expectResult(1);
            }

            static class Handler {

                @HandleGet("/")
                Object parameters(@QueryParam int offset) {
                    return offset;
                }
            }
        }

        static class SomeId extends Id<Object> {
            public SomeId(String functionalId) {
                super(functionalId);
            }
        }

    }

    @Nested
    class WebSocketTests {

        @Nested
        class Sync {
            private final TestFixture testFixture = TestFixture.create(new Handler());

            @Test
            void testOpen() {
                testFixture.whenWebRequest(WebRequest.builder().method(WS_OPEN).url("/auto").build())
                        .expectResult(WebResponse.builder().payload("open").build());
            }

            @Test
            void testResponse() {
                testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/response").build())
                        .expectResult(WebResponse.builder().payload("response").build());
            }

            @Test
            void testNoResponse() {
                testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/noResponse").build())
                        .expectNoResult();
            }

            @Test
            void testViaSession() {
                testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/viaSession").build())
                        .expectWebResponses("viaSession");
            }
        }

        @Nested
        class Async {
            private final TestFixture testFixture = TestFixture.createAsync(new Handler())
                    .resultTimeout(Duration.ofSeconds(1)).consumerTimeout(Duration.ofSeconds(1)).spy();

            @Test
            void testAutoHandshake() {
                testFixture.whenWebRequest(WebRequest.builder().method(WS_HANDSHAKE).url("/auto").build())
                        .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.WEBRESPONSE)).append(
                                any(), any(SerializedMessage.class)))
                        .expectNoErrors();
            }

            @Test
            void testOpen() {
                testFixture.whenWebRequest(WebRequest.builder().method(WS_OPEN).url("/auto").build())
                        .expectWebResponses(WebResponse.builder().payload("open").build());
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
                testFixture.whenWebRequest(WebRequest.builder().method(WS_MESSAGE).url("/viaSession").build())
                        .expectWebResponses("viaSession");
            }
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

    @Nested
    class HeaderTests {

        @Test
        void testWithHeader() {
            TestFixture.create(new Object() {
                @HandleGet("/checkHeader")
                String check(WebRequest request) {
                    return Optional.ofNullable(request.getHeader("foo")).orElseThrow();
                }
            }).withHeader("foo", "bar").whenGet("/checkHeader").expectResult("bar");
        }

        @Test
        void testValidCookieHeader() {
            TestFixture.create(new Object() {
                        @HandleGet("/checkHeader")
                        String check(WebRequest request) {
                            return request.getCookie("foo").map(HttpCookie::getValue).orElse(null);
                        }
                    }).withHeader("cookie", "foo=bar=bar").whenGet("/checkHeader")
                    .expectResult("bar=bar");
        }

        @Test
        void testInvalidCookieHeader() {
            TestFixture.create(new Object() {
                @HandleGet("/checkHeader")
                String check(WebRequest request) {
                    return request.getCookie("").map(HttpCookie::getValue).orElse(null);
                }
            }).withHeader("cookie", "bar").whenGet("/checkHeader")
                    .expectNoResult().expectNoErrors();
        }

        @Test
        void testWithoutHeader() {
            TestFixture.create(new Object() {
                @HandleGet("/checkHeader")
                String check(WebRequest request) {
                    return Optional.ofNullable(request.getHeader("foo")).orElseThrow();
                }
            }).withHeader("foo", "bar")
                    .withoutHeader("foo").whenGet("/checkHeader").expectExceptionalResult();
        }

        @Test
        void testWithCookie() {
            TestFixture.create(new Object() {
                @HandleGet("/checkCookie")
                String check(WebRequest request) {
                    return request.getCookie("foo").orElseThrow().getValue();
                }
            }).withCookie("foo", "bar").whenGet("/checkCookie").expectResult("bar");
        }

        @Test
        void returnedCookieIsUsed() {
            TestFixture.create(new Object() {
                @HandlePost("/signIn")
                WebResponse signIn(String userName) {
                    return WebResponse.builder().cookie(new HttpCookie("user", userName)).build();
                }

                @HandleGet("/getUser")
                String getUser(WebRequest request) {
                    return request.getCookie("user").orElseThrow().getValue();
                }
            }).givenPost("signIn", "testUser").whenGet("getUser").expectResult("testUser");
        }
    }
}
