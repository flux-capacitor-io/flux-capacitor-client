/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.RetryConfiguration;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.Request;
import io.fluxcapacitor.common.api.RequestBatch;
import io.fluxcapacitor.common.api.ResultBatch;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.Properties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;
import static io.fluxcapacitor.javaclient.FluxCapacitor.publishMetrics;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static javax.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;

@Slf4j
public abstract class AbstractWebsocketClient implements AutoCloseable {
    public static WebSocketContainer defaultWebSocketContainer = ContainerProvider.getWebSocketContainer();
    public static ObjectMapper defaultObjectMapper = JsonMapper.builder().disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules().disable(WRITE_DATES_AS_TIMESTAMPS).build();

    private final SessionPool sessionPool;
    private final Properties properties;
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketRequest> requests = new ConcurrentHashMap<>();
    private final Map<String, Backlog<JsonType>> sessionBacklogs = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService resultExecutor = Executors.newFixedThreadPool(8);
    private final boolean sendMetrics;

    public AbstractWebsocketClient(URI endpointUri, Properties properties, boolean sendMetrics) {
        this(endpointUri, properties, sendMetrics, 1);
    }

    public AbstractWebsocketClient(URI endpointUri, Properties properties, boolean sendMetrics, int numberOfSessions) {
        this(defaultWebSocketContainer, endpointUri, properties, sendMetrics, Duration.ofSeconds(1),
             defaultObjectMapper, numberOfSessions);
    }

    public AbstractWebsocketClient(WebSocketContainer container, URI endpointUri, Properties properties,
                                   boolean sendMetrics, Duration reconnectDelay, ObjectMapper objectMapper,
                                   int numberOfSessions) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sendMetrics = sendMetrics;
        this.sessionPool = new SessionPool(numberOfSessions, () -> retryOnFailure(
                () -> container.connectToServer(this, endpointUri),
                RetryConfiguration.builder()
                        .delay(reconnectDelay)
                        .errorTest(e -> !closed.get())
                        .successLogger(s -> log.info("Successfully reconnected to endpoint {}", endpointUri))
                        .exceptionLogger(status -> {
                            if (status.getNumberOfTimesRetried() == 0) {
                                log.warn("Failed to connect to endpoint {}; reason: {}. Retrying every {} ms...",
                                         endpointUri, status.getException().getMessage(),
                                         status.getRetryConfiguration().getDelay().toMillis());
                            }
                        }).build()));
    }

    protected <R extends QueryResult> CompletableFuture<R> send(Request request) {
        return new WebSocketRequest(request, currentCorrelationData()).send();
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    protected <R extends QueryResult> R sendAndWait(Request request) {
        return (R) send(request).get();
    }

    @SneakyThrows
    protected Awaitable sendAndForget(JsonType object) {
        Awaitable awaitable = send(object, sessionPool.get());
        tryPublishMetrics(object.toMetric(), Metadata.empty());
        return awaitable;
    }

    @SneakyThrows
    protected Awaitable send(JsonType object, Session session) {
        return sessionBacklogs.computeIfAbsent(
                session.getId(), id -> new Backlog<>(batch -> sendBatch(batch, session))).add(object);
    }

    @SneakyThrows
    protected Awaitable sendBatch(List<JsonType> requests, Session session) {
        JsonType object = requests.size() == 1  ? requests.get(0) : new RequestBatch<>(requests);
        try (OutputStream outputStream = session.getBasicRemote().getSendStream()) {
            byte[] bytes = objectMapper.writeValueAsBytes(object);
            outputStream.write(compress(bytes, properties.getCompression()));
        } catch (Exception e) {
            log.error("Failed to send request {}", object, e);
            throw e;
        }
        return Awaitable.ready();
    }

    @OnMessage
    @SneakyThrows
    public void onMessage(byte[] bytes) {
        resultExecutor.execute(() -> {
            JsonType value;
            try {
                value = objectMapper.readValue(decompress(bytes, properties.getCompression()), JsonType.class);
            } catch (Exception e) {
                log.error("Could not parse input. Expected a Json message.", e);
                return;
            }
            if (value instanceof ResultBatch) {
                ((ResultBatch) value).getResults().forEach(r -> resultExecutor.execute(() -> handleResult(r)));
            } else {
                handleResult((QueryResult) value);
            }
        });

    }

    protected void handleResult(QueryResult result) {
        try {
            WebSocketRequest webSocketRequest = requests.remove(result.getRequestId());
            if (webSocketRequest == null) {
                log.warn("Could not find outstanding read request for id {}", result.getRequestId());
            } else {
                try {
                    tryPublishMetrics(result.toMetric(),
                                      Metadata.of("requestId", webSocketRequest.request.getRequestId(),
                                                  "msDuration", currentTimeMillis() - webSocketRequest.sendTimestamp)
                                              .with(webSocketRequest.correlationData));
                } finally {
                    webSocketRequest.result.complete(result);
                }
            }
        } catch (Throwable e) {
            log.error("Failed to handle result {}", result, e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessionBacklogs.remove(session.getId());
        if (closeReason.getCloseCode().getCode() > NO_STATUS_CODE.getCode()) {
            log.warn("Connection to endpoint {} closed with reason {}", session.getRequestURI(), closeReason);
        }
        retryOutstandingRequests(session.getId());
    }

    protected void retryOutstandingRequests(String sessionId) {
        if (!closed.get() && !requests.isEmpty()) {
            try {
                sleep(1_000);
            } catch (InterruptedException e) {
                currentThread().interrupt();
                throw new IllegalStateException("Thread interrupted while trying to retry outstanding requests", e);
            }
            requests.values().stream().filter(r -> sessionId.equals(r.sessionId)).forEach(WebSocketRequest::send);
        }
    }

    @OnError
    public void onError(Session session, Throwable e) {
        log.error("Client side error for web socket connected to endpoint {}", session.getRequestURI(), e);
    }

    @Override
    public void close() {
        close(false);
    }

    protected void close(boolean clearOutstandingRequests) {
        if (closed.compareAndSet(false, true)) {
            synchronized (closed) {
                if (clearOutstandingRequests) {
                    requests.clear();
                }
                sessionPool.close();
                if (!requests.isEmpty()) {
                    log.warn("{}: Closed websocket session to endpoint with {} outstanding requests",
                             getClass().getSimpleName(), requests.size());
                }
            }
        }
    }

    protected void tryPublishMetrics(Object metric, Metadata metadata) {
        if (sendMetrics && metric != null) {
            FluxCapacitor.getOptionally().ifPresent(f -> publishMetrics(metric, metadata));
        }
    }

    @RequiredArgsConstructor
    protected class WebSocketRequest {
        private final Request request;
        private final CompletableFuture<QueryResult> result = new CompletableFuture<>();
        private final Map<String, String> correlationData;
        private volatile String sessionId;
        private volatile long sendTimestamp;

        @SuppressWarnings("unchecked")
        protected <T extends QueryResult> CompletableFuture<T> send() {
            Session session;
            try {
                session = sessionPool.get();
            } catch (Exception e) {
                log.error("Failed to get websocket session to send request {}", request, e);
                result.completeExceptionally(e);
                return (CompletableFuture<T>) result;
            }
            this.sessionId = session.getId();
            requests.put(request.getRequestId(), this);

            try {
                sendTimestamp = System.currentTimeMillis();
                AbstractWebsocketClient.this.send(request, session);
                tryPublishMetrics(request.toMetric(), Metadata.of("requestId", request.getRequestId()));
            } catch (Exception e) {
                requests.remove(request.getRequestId());
                result.completeExceptionally(e);
            }
            return (CompletableFuture<T>) result;
        }
    }

}
