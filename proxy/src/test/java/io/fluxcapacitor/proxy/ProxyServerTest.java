package io.fluxcapacitor.proxy;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TestUtils;
import io.fluxcapacitor.common.ThrowingConsumer;
import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.web.HandleGet;
import io.fluxcapacitor.javaclient.web.HandlePost;
import io.fluxcapacitor.javaclient.web.HandleSocketClose;
import io.fluxcapacitor.javaclient.web.HandleSocketMessage;
import io.fluxcapacitor.javaclient.web.HandleSocketOpen;
import io.fluxcapacitor.javaclient.web.HandleSocketPong;
import io.fluxcapacitor.javaclient.web.SocketSession;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.lang.String.format;

@Slf4j
class ProxyServerTest {

    private final TestFixture testFixture = TestFixture.createAsync();
    private final int proxyPort = TestUtils.getAvailablePort();
    private final ProxyRequestHandler proxyRequestHandler =
            new ProxyRequestHandler(testFixture.getFluxCapacitor().client());
    private final Registration proxyServer = ProxyServer.start(proxyPort, proxyRequestHandler);

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @AfterEach
    void tearDown() {
        proxyServer.cancel();
    }

    @Nested
    class Basic {
        @Test
        void get() {
            testFixture.registerHandlers(new Object() {
                        @HandleGet("/")
                        String hello() {
                            return "Hello World";
                        }
                    })
                    .whenApplying(fc -> httpClient.send(newRequest().GET().build(),
                                                        BodyHandlers.ofString()).body())
                    .expectResult("Hello World");
        }

        @Test
        void post() {
            testFixture.registerHandlers(new Object() {
                        @HandlePost("/")
                        String hello(String name) {
                            return "Hello " + name;
                        }
                    })
                    .whenApplying(fc -> httpClient.send(newRequest().POST(BodyPublishers.ofString("Flux")).build(),
                                                        BodyHandlers.ofString()).body())
                    .expectResult("Hello Flux");
        }

        private HttpRequest.Builder newRequest() {
            return HttpRequest.newBuilder(baseUri());
        }

        private URI baseUri() {
            return URI.create(format("http://127.0.0.1:%s/", proxyPort));
        }
    }

    @Nested
    @DisabledIfEnvironmentVariable(named = "BUILD_ENVIRONMENT", matches = "github")
    class Websocket {
        @Test
        void openSocket() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketOpen("/")
                        String hello() {
                            return "Hello World";
                        }
                    })
                    .whenApplying(openSocketAndWait())
                    .expectResult("Hello World");
        }

        @Test
        void sendMessage() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketMessage("/")
                        String hello(String name) {
                            return "Hello " + name;
                        }
                    })
                    .whenApplying(openSocketAnd(webSocket -> webSocket.sendText("Flux", true)))
                    .expectResult("Hello Flux");
        }

        @Test
        void sendMessageViaSocketParam() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketMessage("/")
                        void hello(String name, SocketSession session) {
                            session.sendMessage("Hello " + name);
                        }
                    })
                    .whenApplying(openSocketAnd(webSocket -> webSocket.sendText("Flux", true)))
                    .expectResult("Hello Flux");
        }

        @Test
        void sendPing() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketOpen("/")
                        void open(SocketSession session) {
                            session.sendPing("ping");
                        }

                        @HandleSocketPong("/")
                        void pong(String pong, SocketSession session) {
                            session.sendMessage("got pong " + pong);
                        }
                    })
                    .whenApplying(openSocketAndWait())
                    .expectResult("got pong ping");
        }

        @Test
        void closeSocketExternally() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketClose("/")
                        void close(Integer reason) {
                            FluxCapacitor.publishEvent("ws closed with " + reason);
                        }
                    })
                    .whenApplying(openSocketAnd(ws -> {
                        ws.sendClose(1000, "bla");
                        Thread.sleep(100);
                    }))
                    .expectResult("1000")
                    .expectEvents("ws closed with 1000");
        }

        @Test
        void closeSocketFromApplication() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketOpen("/")
                        void open(SocketSession session) {
                            session.close(1001);
                        }
                        @HandleSocketClose("/")
                        void close(Integer reason) {
                            log.info("ws closed with " + reason);
                            FluxCapacitor.publishEvent("ws closed with " + reason);
                        }
                    })
                    .whenApplying(openSocketAnd(ws -> Thread.sleep(100)))
                    .expectResult("1001")
                    .expectEvents("ws closed with 1001");
        }

        @Test
        void closeProxy() {
            testFixture.registerHandlers(new Object() {
                        @HandleSocketClose("/")
                        void close(Integer code) {
                            FluxCapacitor.publishEvent("ws closed with " + code);
                        }
                    })
                    .whenApplying(openSocketAnd(ws -> {
                        Thread.sleep(100);
                        proxyRequestHandler.close();
                        Thread.sleep(100);
                    }))
                    .expectEvents("ws closed with 1001");
        }

        private ThrowingFunction<FluxCapacitor, ?> openSocketAndWait() {
            return openSocketAnd(ws -> {});
        }

        private ThrowingFunction<FluxCapacitor, ?> openSocketAnd(ThrowingConsumer<WebSocket> followUp) {
            return fc -> {
                CompletableFuture<String> result = new CompletableFuture<>();
                WebSocket webSocket = openSocket(result);
                followUp.accept(webSocket);
                return result.get();
            };
        }

        @SneakyThrows
        private WebSocket openSocket(CompletableFuture<String> callback) {
            return httpClient.newWebSocketBuilder().buildAsync(baseUri(), new WebSocket.Listener() {
                @Override
                public CompletionStage<?> onText(WebSocket webSocket1, CharSequence data, boolean last) {
                    callback.complete(String.valueOf(data));
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    callback.complete(Integer.toString(statusCode));
                    return null;
                }
            }).get();
        }

        private URI baseUri() {
            return URI.create(format("ws://localhost:%s/", proxyPort));
        }
    }
}