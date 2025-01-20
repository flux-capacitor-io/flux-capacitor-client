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

package io.fluxcapacitor.proxy;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TestUtils;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.common.serialization.compression.CompressionUtils;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.GET;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.POST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@Slf4j
class ForwardProxyConsumerTest {
    private final TestFixture testFixture = TestFixture.createAsync().spy();
    private final int port = TestUtils.getAvailablePort();

    private HttpContext serverContext;
    private Registration registration;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        registration = ForwardProxyConsumer.start(testFixture.getFluxCapacitor().client());
        HttpServer server = HttpServer.create(
                new InetSocketAddress("localhost", port), 0);
        serverContext = server.createContext("/");
        ExecutorService executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.start();
        log.info(" Server started on port {}", port);
        registration = registration.merge(() -> {
            server.stop(0);
            executor.shutdownNow();
        });
    }

    @AfterEach
    void tearDown() {
        registration.cancel();
    }

    @Test
    void getRequest() {
        serverContext.setHandler(exchange -> {
            try (OutputStream outputStream = exchange.getResponseBody()) {
                String response = "test";
                exchange.sendResponseHeaders(200, response.length());
                outputStream.write(response.getBytes());
                outputStream.flush();
            }
        });
        testFixture.whenWebRequest(WebRequest.builder().url("http://localhost:" + port).method(GET).build())
                .<WebResponse>expectResult(r -> r.getStatus() == 200
                                                       && "test".equals(new String(r.<byte[]>getPayload())));
    }

    @Test
    void handlerMetricsPublished() {
        serverContext.setHandler(exchange -> {
            try (OutputStream outputStream = exchange.getResponseBody()) {
                String response = "test";
                exchange.sendResponseHeaders(200, response.length());
                outputStream.write(response.getBytes());
                outputStream.flush();
            }
        });
        testFixture
                .whenWebRequest(WebRequest.builder().url("http://localhost:" + port).method(GET).build())
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.METRICS), atLeastOnce())
                        .append(any(), any(SerializedMessage.class)));
    }

    @Test
    void getRequestZipped() {
        serverContext.setHandler(exchange -> {
            try (OutputStream outputStream = exchange.getResponseBody()) {
                exchange.getResponseHeaders().add("Content-Encoding", "gzip");
                byte[] compressed = CompressionUtils.compress("test".getBytes(), CompressionAlgorithm.GZIP);
                exchange.sendResponseHeaders(200, compressed.length);
                outputStream.write(compressed);
                outputStream.flush();
            }
        });
        testFixture
                .whenWebRequest(WebRequest.builder().url("http://localhost:" + port).method(GET).build())
                .<WebResponse>expectResultMessage(
                        r -> r.getStatus() == 200 && "test".equals(new String(r.<byte[]>getPayload())));
    }

    @Test
    void postRequest() {
        serverContext.setHandler(exchange -> exchange.sendResponseHeaders(204, -1));
        testFixture
                .whenWebRequest(WebRequest.builder().url("http://localhost:" + port)
                                                     .payload("test").method(POST).build())
                .<WebResponse>expectResult(r -> r.getStatus() == 204 && r.<byte[]>getPayload().length == 0)
                .expectWebResponse(r -> r.getStatus() == 204 && r.getMetadata().containsKey("$correlationId"));
    }
}