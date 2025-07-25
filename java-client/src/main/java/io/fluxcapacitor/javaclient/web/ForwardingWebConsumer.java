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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.publishing.correlation.DefaultCorrelationDataProvider;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;
import static java.net.http.HttpRequest.BodyPublishers.ofByteArray;

/**
 * A specialized, opt-in {web request consumer that forwards incoming
 * {@link io.fluxcapacitor.common.MessageType#WEBREQUEST} messages to a locally running HTTP server.
 * <p>
 * This class is internally initialized when
 * {@link io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder#forwardWebRequestsToLocalServer(int)} is
 * configured. Rather than routing messages through Flux’s internal handler infrastructure, it converts web requests
 * into raw HTTP requests and asynchronously sends them to {@code http://localhost:port}.
 *
 * <h2>Purpose</h2>
 * This mechanism exists primarily for interoperability: it allows applications to integrate with their own HTTP servers
 * (e.g., frameworks like Spring Boot, Jooby, or Vert.x) rather than adopting the Flux message-based web handler
 * framework.
 *
 * <h2><span style="color:red;">⚠️ Caution: Limited Use Case</span></h2>
 * While supported, use of this component is generally discouraged. It bypasses the core pull-based message consumption
 * model of Flux Capacitor by pushing messages asynchronously to a local server. This introduces a risk of losing
 * delivery guarantees, tracing consistency, and handler lifecycle control.
 * <br><br>
 * Consider using {@code @HandleWeb}-based handlers and declarative routing instead.
 *
 * <h2>How It Works</h2>
 * <ul>
 *   <li>Consumes {@code WEBREQUEST} messages via a {@link io.fluxcapacitor.javaclient.tracking.Tracker}</li>
 *   <li>For each message, creates a corresponding {@link java.net.http.HttpRequest} and sends it to localhost</li>
 *   <li>Captures the {@link java.net.http.HttpResponse} and converts it back into a {@link io.fluxcapacitor.common.api.SerializedMessage}</li>
 *   <li>Publishes the result using the {@code WEBRESPONSE} {@link GatewayClient}</li>
 * </ul>
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Header forwarding with filtering of restricted headers</li>
 *   <li>Support for request correlation and metadata propagation</li>
 *   <li>Graceful handling of 404s (configurable via {@code LocalServerConfig#isIgnore404()})</li>
 *   <li>Fallback error response creation if the target server fails</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder#forwardWebRequestsToLocalServer(int)
 * @see io.fluxcapacitor.javaclient.web.WebRequest
 * @see io.fluxcapacitor.javaclient.web.WebResponse
 * @see io.fluxcapacitor.javaclient.tracking.Tracker
 */
@Slf4j
public class ForwardingWebConsumer implements AutoCloseable {
    private final String host;
    private final LocalServerConfig localServerConfig;
    private final ConsumerConfiguration configuration;
    private final HttpClient httpClient;
    private final AtomicReference<Registration> registration = new AtomicReference<>();

    public ForwardingWebConsumer(LocalServerConfig localServerConfig, ConsumerConfiguration configuration) {
        this.host = "http://localhost:" + localServerConfig.getPort();
        this.localServerConfig = localServerConfig;
        this.configuration = configuration;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Synchronized
    public void start(FluxCapacitor fluxCapacitor) {
        close();
        GatewayClient gatewayClient = fluxCapacitor.client().getGatewayClient(MessageType.WEBRESPONSE);
        BiConsumer<SerializedMessage, SerializedMessage> gateway = (request, response) -> {
            response.setTarget(request.getSource());
            response.setRequestId(request.getRequestId());
            response.setMetadata(response.getMetadata().with(currentCorrelationData()));
            gatewayClient.append(Guarantee.NONE, response);
        };
        Consumer<List<SerializedMessage>> consumer = messages -> messages.forEach(m -> {
            Map<String, String> correlationData = getCorrelationData(m);
            try {
                HttpRequest request = createRequest(m);
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                        .whenComplete((r, e) -> {
                            if (e == null && r.statusCode() == 404 && localServerConfig.isIgnore404()) {
                                return;
                            }
                            gateway.accept(m, e == null ? toMessage(r, correlationData) : toMessage(e, correlationData));
                        });
            } catch (Exception e) {
                try {
                    gateway.accept(m, toMessage(e, correlationData));
                } catch (Exception e2) {
                    log.error("Failed to create response message from exception", e2);
                }
            }
        });
        registration.getAndUpdate(r -> r == null ? DefaultTracker.start(consumer, MessageType.WEBREQUEST,
                                                                        configuration, fluxCapacitor) : r);
    }

    protected Map<String, String> getCorrelationData(SerializedMessage m) {
        try {
            return FluxCapacitor.getOptionally().map(FluxCapacitor::correlationDataProvider).orElse(
                    DefaultCorrelationDataProvider.INSTANCE).getCorrelationData(new DeserializingMessage(
                    m, type -> null, MessageType.WEBRESPONSE, null, null));
        } catch (Exception e) {
            log.error("Failed to get correlation data for request message", e);
            return Collections.emptyMap();
        }
    }

    protected HttpRequest createRequest(SerializedMessage m) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(new URI(host + WebRequest.getUrl(m.getMetadata())))
                    .method(WebRequest.getMethod(m.getMetadata()),
                            m.getData().getValue().length == 0 ? HttpRequest.BodyPublishers.noBody() :
                                    ofByteArray(m.getData().getValue()));

            String[] headers = WebRequest.getHeaders(m.getMetadata()).entrySet().stream()
                    .filter(e -> !isRestricted(e.getKey()))
                    .flatMap(e -> e.getValue().stream().flatMap(v -> Stream.of(e.getKey(), v)))
                    .toArray(String[]::new);
            if (headers.length > 0) {
                builder.headers(headers);
            }
            if (m.getData().getFormat() != null) {
                builder.header("Content-Type", m.getData().getFormat());
            }
            return builder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create HttpRequest", e);
        }
    }

    protected boolean isRestricted(String headerName) {
        return Set.of("connection", "content-length", "expect", "host", "upgrade").contains(headerName.toLowerCase());
    }

    protected SerializedMessage toMessage(HttpResponse<byte[]> response,
                                          Map<String, String> correlationData) {
        HttpHeaders headers = response.headers();
        Metadata metadata = Metadata.of(correlationData)
                .with(WebResponse.asMetadata(response.statusCode(), headers.map()));
        return new SerializedMessage(new Data<>(response.body(), null, 0,
                                                headers.firstValue("content-type").orElse(null)), metadata,
                                     FluxCapacitor.generateId(), System.currentTimeMillis());
    }

    protected SerializedMessage toMessage(Throwable error,
                                          Map<String, String> correlationData) {
        log.error("Failed to handle web request: " + error.getMessage() + ". Continuing with next request.", error);
        return new SerializedMessage(
                new Data<>("The request failed due to a server error".getBytes(), null, 0, "text/plain"),
                Metadata.of(correlationData).with(WebResponse.asMetadata(500, Collections.emptyMap())),
                FluxCapacitor.generateId(), System.currentTimeMillis());
    }

    @Override
    public void close() {
        registration.getAndUpdate(r -> {
            if (r != null) {
                r.cancel();
            }
            return null;
        });
    }
}
