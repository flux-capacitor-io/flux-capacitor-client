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
import com.fasterxml.jackson.databind.node.TextNode;
import io.fluxcapacitor.common.FileUtils;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ThrowingPredicate;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.modeling.Id;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.handling.Request;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.FixedUserProvider;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.MockUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.RequiresUser;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.web.SocketEndpoint.AliveCheck;
import io.fluxcapacitor.javaclient.web.path.ClassPathHandler;
import io.fluxcapacitor.javaclient.web.path.PackagePathHandler;
import io.fluxcapacitor.javaclient.web.path.subpath.SubPathHandler;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.GET;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.POST;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.PUT;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.TRACE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_CLOSE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_HANDSHAKE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_MESSAGE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_OPEN;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_PONG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

public class HandleWebTest {

    @Nested
    class GenericTests {
        private final TestFixture testFixture = TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(
                new FixedUserProvider(() -> null)), new Handler());

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
                    .<WebResponse>expectResultMessage(r -> r.getStatus() == 200)
                    .mapResult(r -> (String) r)
                    .expectResult("payload");
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
            testFixture.whenWebRequest(WebRequest.builder().method(POST).url("/requiresUser")
                                               .payload(new PayloadRequiringUser()).build())
                    .expectExceptionalResult(UnauthenticatedException.class);
        }

        @Test
        void testGetUser_validWithUser() {
            TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(
                            new FixedUserProvider(new MockUser())), new Handler())
                    .whenGet("/getUser")
                    .expectResult(User.class)
                    .expectWebResponse(r -> r.getStatus() == 200);
        }

        @Test
        void testGetUser_exceptionWithoutUser() {
            testFixture.whenGet("/getUser")
                    .expectExceptionalResult(UnauthenticatedException.class);
        }

        @Test
        void testGetUserIfAuthenticated_timeoutWithoutUser() {
            testFixture.whenGet("/getUserIfAuthenticated")
                    .expectExceptionalResult(TimeoutException.class);
        }

        @Test
        void testExpectWebRequestEmptyPayload() {
            TestFixture.create(new Object() {
                        @HandlePost("/foo")
                        void foo() {
                        }
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

            @HandleGet("/get")
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

            @HandleGet("/getUser")
            @RequiresUser
            Object getUser(User user) {
                return user;
            }

            @HandleGet("/getUserIfAuthenticated")
            @RequiresUser(throwIfUnauthorized = false)
            Object getUserIfAuthenticated(User user) {
                return user;
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

        final TestFixture testFixture = TestFixture.create(new ClassPathHandler(), new PackagePathHandler(), new SubPathHandler());

        @Test
        void classPathTest() {
            testFixture.whenGet("/class/get").expectResult("get");
        }

        @Test
        void packagePathTest() {
            testFixture.whenGet("/package/get").expectResult("get");
        }

        @Test
        void subPathTest() {
            testFixture.whenGet("/package/sub/class/get").expectResult("get");
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
        void testCustomMethod() {
            testFixture
                    .whenWebRequest(WebRequest.builder().method("CUSTOM").url("/string3").payload("payload").build())
                    .expectResult("payload");
        }

        @Test
        void testAnyMethod() {
            testFixture
                    .whenPut("/string4", "payload")
                    .expectResult("payload")
                    .andThen()
                    .whenWebRequest(WebRequest.builder().method(TRACE).url("/string4").payload("payload").build())
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

            @HandleWeb(value = "/string3", method = "CUSTOM")
            String customMethod(String body) {
                return body;
            }

            @HandleWeb(value = "/string4")
            String anyMethod(String body) {
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

            @Test
            void testPathParam_Path() {
                testFixture.whenGet("/path/123").expectResult(new SomeId("123"));
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

                @Path("/path/{foo}")
                @HandleGet
                Object getPath(@PathParam SomeId foo) {
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
    class StaticFileTests {
        private final TestFixture testFixture = TestFixture.create(new ClasspathHandler(), new FileSystemHandler());

        @Test
        void normalGet() {
            testFixture.whenGet("/web/get").expectResult("dynamicGet");
        }

        @Test
        void serveHtmlFile() {
            testFixture.whenGet("/static/index.html")
                    .expectResult(testContents("<!DOCTYPE html>"));
        }

        @Test
        void serveLogo() {
            testFixture.whenGet("/static/assets/logo.svg")
                    .expectResult(testContents("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        }

        @Test
        void serveLogo_relative() {
            TestFixture.create(new RelativeClasspathHandler()).whenGet("/web/static/assets/logo.svg")
                    .expectResult(testContents("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        }

        @Test
        void serveLogo_fs() {
            testFixture.whenGet("/file/assets/logo.svg")
                    .expectResult(testContents("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        }

        @Test
        void serveFallback() {
            testFixture.whenGet("/static/whatever")
                    .expectResult(testContents("<!DOCTYPE html>"));
        }

        @Test
        void serveRoot() {
            testFixture.whenGet("/static")
                    .expectWebResult(testContents("<!DOCTYPE html>"))
                    .andThen()
                    .whenGet("/static/")
                    .expectWebResult(testContents("<!DOCTYPE html>"))
                    .andThen()
                    .whenGet("/file")
                    .expectWebResult(testContents("<!DOCTYPE html>"))
                    .andThen()
                    .whenGet("/file/")
                    .expectWebResult(testContents("<!DOCTYPE html>"));
        }

        @Path
        @ServeStatic(value = "/static", resourcePath = "classpath:/web/static")
        static class ClasspathHandler {
            @HandleGet("/get")
            String get() {
                return "dynamicGet";
            }
        }

        @Path
        @ServeStatic(value = "static", resourcePath = "classpath:/web/static")
        static class RelativeClasspathHandler {
        }

        static class FileSystemHandler extends StaticFileHandler {
            public FileSystemHandler() {
                super("/file", Paths.get(FileUtils.getFile(
                        FileSystemHandler.class, "/web/static/index.html")).toAbsolutePath().toString()
                        .replace("/index.html", ""), "index.html");
            }
        }

        static ThrowingPredicate<WebResponse> testContents(String string) {
            return r -> {
                try (InputStream input = r.getPayload()) {
                    var contents = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    return contents.contains(string);
                }
            };
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

        @Nested
        class SocketEndpointTests {
            static final String endpointUrl = "/endpoint";
            private final TestFixture testFixture = TestFixture.create();

            @Nested
            class ConstructorTests {

                @BeforeEach
                void setUp() {
                    testFixture.registerHandlers(Endpoint.class);
                }

                @Test
                void testOpen() {
                    testFixture.whenWebRequest(toWebRequest(WS_OPEN))
                            .expectWebResponses("open: testSession");
                }

                @Test
                void testMessageWithoutOpenNotPossible() {
                    testFixture.whenWebRequest(toWebRequest(WS_MESSAGE))
                            .expectError();
                }

                @Test
                void testMessageWithOpen() {
                    testFixture
                            .givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_MESSAGE))
                            .expectWebResponses("response: testSession");
                }

                @Test
                void testMessageOtherUrl() {
                    testFixture
                            .givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_MESSAGE).toBuilder()
                                                    .metadata(Metadata.of("sessionId", "otherSession"))
                                                    .url("/other").build())
                            .expectExceptionalResult();
                }

                @SocketEndpoint
                @Path(endpointUrl)
                static class Endpoint {
                    private final SocketSession session;

                    @HandleSocketOpen
                    Endpoint(SocketSession session) {
                        this.session = session;
                        session.sendMessage("open: " + session.sessionId());
                    }

                    @HandleSocketMessage
                    String response() {
                        return "response: " + session.sessionId();
                    }
                }
            }

            @Nested
            @Path(endpointUrl)
            class DefaultConstructorTests {

                @BeforeEach
                void setUp() {
                    testFixture.registerHandlers(Endpoint.class);
                }

                @Test
                void testOpen() {
                    testFixture.whenWebRequest(toWebRequest(WS_OPEN))
                            .expectWebResponses("open: testSession");
                }

                @Test
                void testMessage() {
                    testFixture.whenWebRequest(toWebRequest(WS_MESSAGE))
                            .expectWebResponses("response: testSession");
                }

                @Test
                void testEventAfterOpen() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenEvent(123)
                            .expectEvents("123");
                }

                @Test
                void testEventBeforeOpen() {
                    testFixture
                            .whenEvent(123)
                            .expectEvents("not open")
                            .expectNoErrors();
                }

                @SocketEndpoint
                static class Endpoint {
                    @HandleEvent
                    static void handleBefore(Integer event) {
                        FluxCapacitor.publishEvent("not open");
                    }

                    @HandleSocketOpen
                    Object onOpen(SocketSession session) {
                        return "open: " + session.sessionId();
                    }

                    @HandleSocketMessage
                    String onMessage(SocketSession session) {
                        return "response: " + session.sessionId();
                    }

                    @HandleEvent
                    void handle(Integer event) {
                        FluxCapacitor.publishEvent(event.toString());
                    }
                }
            }

            @Nested
            class PingTests {

                static final int pingDelay = 30, pingTimeout = 10;

                @BeforeEach
                void setUp() {
                    testFixture.registerHandlers(Endpoint.class);
                }

                @Test
                void testSendPing() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenTimeElapses(Duration.ofSeconds(pingDelay))
                            .expectWebResponse(r -> "ping".equals(r.getMetadata().get("function")));
                }

                @Test
                void testRescheduleAfterPong() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .givenElapsedTime(Duration.ofSeconds(pingDelay))
                            .givenWebRequest(toWebRequest(WS_PONG))
                            .whenTimeElapses(Duration.ofSeconds(pingDelay))
                            .expectWebResponse(r -> "ping".equals(r.getMetadata().get("function")))
                            .expectNoEvents();
                }

                @Test
                void closeAfterPingTimeout() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenTimeElapses(Duration.ofSeconds(pingDelay))
                            .andThen()
                            .whenTimeElapses(Duration.ofSeconds(pingTimeout))
                            .expectWebResponse(r -> "close".equals(r.getMetadata().get("function")))
                            .expectEvents("close: testSession");
                }

                @Test
                void testMessage() {
                    testFixture.whenWebRequest(toWebRequest(WS_MESSAGE))
                            .expectWebResponses("response: testSession");
                }

                @SocketEndpoint(aliveCheck = @AliveCheck(pingDelay = pingDelay, pingTimeout = pingTimeout))
                @Path(endpointUrl)
                static class Endpoint {

                    @HandleSocketOpen
                    Object onOpen(SocketSession session) {
                        return "open: " + session.sessionId();
                    }

                    @HandleSocketMessage
                    String onMessage(SocketSession session) {
                        return "response: " + session.sessionId();
                    }

                    @HandleSocketClose
                    void onClose(SocketSession session) {
                        FluxCapacitor.publishEvent("close: " + session.sessionId());
                    }
                }
            }

            @Nested
            class RequestTests {

                @BeforeEach
                void setUp() {
                    testFixture.registerHandlers(Endpoint.class);
                }

                @Test
                void testSendingRequest() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_MESSAGE, SocketResponse.success(SocketRequest.counter.get(), new MyResponse("out"))))
                            .expectEvents("out");
                }

                @Test
                void testRequestTimeout() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenTimeElapses(Endpoint.requestTimeout)
                            .expectEvents("timeout");
                }

                @Test
                void testReceivingFailedResponse() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_MESSAGE, SocketResponse.error(SocketRequest.counter.get(), "failed")))
                            .expectEvents("failed");
                }

                @Test
                void testDeserializeRequest() {
                    testFixture.whenApplying(fc -> JsonUtils.convertValue(
                            SocketRequest.valueOf("hello"), SocketRequest.class).getRequest())
                            .expectResult(TextNode.valueOf("hello"));
                }

                @Test
                void testReceivingRequest() {
                    SocketRequest request = SocketRequest.valueOf("hello");
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_MESSAGE, request))
                            .mapResult(r -> ((WebResponse) r).getPayload())
                            .expectResult(SocketResponse.success(request.getRequestId(), "hello world"));
                }

                @Test
                void closeOpenRequestsOnClose() {
                    testFixture.givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_CLOSE))
                            .expectEvents("Websocket session testSession has closed");
                }

                @SocketEndpoint
                @Path(endpointUrl)
                static class Endpoint {

                    static final Duration requestTimeout = Duration.ofSeconds(10);

                    @HandleSocketOpen
                    void onOpen(SocketSession session) {
                        session.sendRequest(new MyRequest("in"), requestTimeout)
                                .whenComplete((r, e) -> {
                            if (e == null) {
                                FluxCapacitor.publishEvent(r.getOutput());
                            } else {
                                switch (e) {
                                    case TimeoutException te -> FluxCapacitor.publishEvent("timeout");
                                    default -> FluxCapacitor.publishEvent(e.getMessage());
                                }
                            }
                        });
                    }

                    @HandleSocketMessage
                    String onMessage(String question) {
                        return question + " world";
                    }
                }

                @Value
                static class MyRequest implements Request<MyResponse> {
                    String input;
                }

                @Value
                static class MyResponse {
                    String output;
                }
            }

            @Nested
            class FactoryMethodTests {

                @BeforeEach
                void setUp() {
                    testFixture.registerHandlers(Endpoint.class);
                }

                @Test
                void testOpen() {
                    testFixture.whenWebRequest(toWebRequest(WS_OPEN))
                            .expectWebResponses("open: testSession");
                }

                @Test
                void testCloseOnThrow() {
                    testFixture.whenWebRequest(toWebRequest(WS_OPEN).toBuilder().url(endpointUrl + "/throw").build())
                            .expectWebResponse(r -> r.getMetadata().get("function").equals("close"));
                }

                @Test
                void testOpenRequiresUser_withUser() {
                    TestFixture.create(DefaultFluxCapacitor.builder().registerUserProvider(new FixedUserProvider(new MockUser())), Endpoint.class)
                            .whenWebRequest(toWebRequest(WS_OPEN).toBuilder().url(endpointUrl + "/user").build())
                            .expectSuccessfulResult()
                            .expectWebResponses("open with user: testSession");
                }

                @Test
                void testOpenRequiresUser_withoutUser() {
                    testFixture.withProductionUserProvider()
                            .whenWebRequest(toWebRequest(WS_OPEN).toBuilder().url(endpointUrl + "/user").build())
                            .expectExceptionalResult(UnauthenticatedException.class)
                            .expectWebResponse(r -> "close".equals(r.getMetadata().get("function")));
                }

                @Test
                void testMessage() {
                    testFixture
                            .givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_MESSAGE))
                            .expectWebResponses("foo: testSession");
                }

                @Test
                void testClose() {
                    testFixture
                            .givenWebRequest(toWebRequest(WS_OPEN))
                            .whenWebRequest(toWebRequest(WS_CLOSE))
                            .expectNoResult()
                            .expectNoWebResponses()
                            .expectEvents("close: testSession")
                            .andThen()
                            .whenWebRequest(toWebRequest(WS_MESSAGE))
                            .expectError();
                }

                @SocketEndpoint
                @Path(endpointUrl)
                @AllArgsConstructor(access = AccessLevel.PRIVATE)
                static class Endpoint {

                    private final String name;

                    @HandleSocketOpen("throw")
                    static Endpoint throwOnOpen(SocketSession session) {
                        throw new MockException("error");
                    }

                    @HandleSocketOpen("user")
                    @RequiresUser
                    static Endpoint onOpenWithUser(SocketSession session) {
                        session.sendMessage("open with user: " + session.sessionId());
                        return new Endpoint("withUser");
                    }

                    @HandleSocketOpen
                    static Endpoint onOpen(SocketSession session) {
                        session.sendMessage("open: " + session.sessionId());
                        return new Endpoint("foo");
                    }

                    @HandleSocketMessage
                    String onMessage(SocketSession session) {
                        return name + ": " + session.sessionId();
                    }

                    @HandleSocketClose
                    void onClose(SocketSession session) {
                        FluxCapacitor.publishEvent("close: " + session.sessionId());
                    }
                }
            }

            private WebRequest toWebRequest(String method) {
                return toWebRequest(method, "hello");
            }

            private WebRequest toWebRequest(String method, Object payload) {
                return WebRequest.builder().method(method).url(endpointUrl)
                        .metadata(Metadata.of("sessionId", "testSession"))
                        .payload(payload).build();
            }
        }


        static class Handler {
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
