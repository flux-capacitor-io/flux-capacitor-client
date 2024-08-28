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

package io.fluxcapacitor.javaclient.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.RetryConfiguration;
import io.fluxcapacitor.common.api.Command;
import io.fluxcapacitor.common.api.ErrorResult;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.Request;
import io.fluxcapacitor.common.api.RequestBatch;
import io.fluxcapacitor.common.api.ResultBatch;
import io.fluxcapacitor.common.tracking.InMemoryTaskScheduler;
import io.fluxcapacitor.common.tracking.TaskScheduler;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.exception.ServiceException;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;
import io.fluxcapacitor.javaclient.publishing.AdhocDispatchInterceptor;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.PongMessage;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
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
import static io.fluxcapacitor.common.Guarantee.STORED;
import static io.fluxcapacitor.common.MessageType.METRICS;
import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;
import static io.fluxcapacitor.javaclient.FluxCapacitor.publishMetrics;
import static io.fluxcapacitor.javaclient.common.ClientUtils.ignoreMarker;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static io.fluxcapacitor.javaclient.publishing.AdhocDispatchInterceptor.getAdhocInterceptor;
import static jakarta.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.util.Optional.ofNullable;

public abstract class AbstractWebsocketClient implements AutoCloseable {
    public static WebSocketContainer defaultWebSocketContainer = ContainerProvider.getWebSocketContainer();
    public static ObjectMapper defaultObjectMapper = JsonMapper.builder().disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules().disable(WRITE_DATES_AS_TIMESTAMPS).build();

    @Getter(lazy = true)
    @Accessors(fluent = true)
    private final Logger log = LoggerFactory.getLogger("%s.%s".formatted(getClass().getPackageName(), this));

    private final SessionPool sessionPool;
    private final WebSocketClient client;
    private final ClientConfig clientConfig;
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketRequest> requests = new ConcurrentHashMap<>();
    private final Map<String, Backlog<Request>> sessionBacklogs = new ConcurrentHashMap<>();
    private final TaskScheduler pingScheduler;
    private final Map<String, PingRegistration> pingDeadlines = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ExecutorService resultExecutor;
    private final boolean allowMetrics;

    @Getter(value = AccessLevel.PROTECTED, lazy = true)
    private final Serializer fallbackSerializer = new JacksonSerializer();

    public AbstractWebsocketClient(URI endpointUri, WebSocketClient client, boolean allowMetrics) {
        this(endpointUri, client, allowMetrics, 1);
    }

    public AbstractWebsocketClient(URI endpointUri, WebSocketClient client, boolean allowMetrics,
                                   int numberOfSessions) {
        this(defaultWebSocketContainer, endpointUri, client, allowMetrics, Duration.ofSeconds(1),
             defaultObjectMapper, numberOfSessions);
    }

    public AbstractWebsocketClient(WebSocketContainer container, URI endpointUri, WebSocketClient client,
                                   boolean allowMetrics, Duration reconnectDelay, ObjectMapper objectMapper,
                                   int numberOfSessions) {
        this.client = client;
        this.clientConfig = client.getClientConfig();
        this.objectMapper = objectMapper;
        this.allowMetrics = allowMetrics;
        this.pingScheduler = new InMemoryTaskScheduler(this + "-pingScheduler");
        this.resultExecutor = Executors.newFixedThreadPool(
                8, newThreadFactory(this + "-onMessage"));
        this.sessionPool = new SessionPool(numberOfSessions, () -> retryOnFailure(
                () -> container.connectToServer(this, endpointUri),
                RetryConfiguration.builder()
                        .delay(reconnectDelay)
                        .errorTest(e -> !closed.get())
                        .successLogger(s -> log().info("Successfully reconnected to endpoint {}", endpointUri))
                        .exceptionLogger(status -> {
                            if (status.getNumberOfTimesRetried() == 0) {
                                log().warn("Failed to connect to endpoint {}; reason: {}. Retrying every {} ms...",
                                         endpointUri, status.getException().getMessage(),
                                         status.getRetryConfiguration().getDelay().toMillis());
                            } else if (status.getNumberOfTimesRetried() % 100 == 0) {
                                log().warn("Still trying to connect to endpoint {}. Last error: {}.",
                                         endpointUri, status.getException().getMessage());
                            }
                        }).build()));
    }

    protected <R extends QueryResult> CompletableFuture<R> send(Request request) {
        return new WebSocketRequest(request, currentCorrelationData(),
                                    getAdhocInterceptor(METRICS).orElse(null),
                                    FluxCapacitor.getOptionally().orElse(null)).send();
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    protected <R extends QueryResult> R sendAndWait(Request request) {
        return (R) send(request).get();
    }

    protected CompletableFuture<Void> sendCommand(Command command) {
        return switch (command.getGuarantee()) {
            case NONE -> {
                sendAndForget(command);
                yield CompletableFuture.completedFuture(null);
            }
            case SENT -> sendAndForget(command);
            default -> send(command).thenApply(r -> null);
        };
    }

    @SneakyThrows
    private CompletableFuture<Void> sendAndForget(Command object) {
        return send(object, FluxCapacitor.currentCorrelationData(), sessionPool.get(object.routingKey()));
    }

    @SneakyThrows
    private CompletableFuture<Void> send(Request request, Map<String, String> correlationData,
                                         Session session) {
        try {
            return sessionBacklogs.computeIfAbsent(
                    session.getId(), id -> Backlog.forConsumer(batch -> sendBatch(batch, session))).add(request);
        } finally {
            tryPublishMetrics(request, metricsMetadata().with(correlationData)
                    .with("sessionId", session.getId()).with("requestId", request.getRequestId()));
        }
    }

    @SneakyThrows
    private void sendBatch(List<Request> requests, Session session) {
        JsonType object = requests.size() == 1 ? requests.getFirst() : new RequestBatch<>(requests);
        try (OutputStream outputStream = session.getBasicRemote().getSendStream()) {
            byte[] bytes = objectMapper.writeValueAsBytes(object);
            if (session.isOpen()) {
                outputStream.write(compress(bytes, clientConfig.getCompression()));
            }
        } catch (Exception e) {
            log().error(ignoreMarker, "Failed to send request %s".formatted(object), e);
            if (ofNullable(e.getMessage()).map(m -> m.contains("Channel is closed")).orElse(false)) {
                abort(session);
            } else {
                throw e;
            }
        }
    }

    @OnMessage
    public void onMessage(byte[] bytes, Session session) {
        resultExecutor.execute(() -> {
            JsonType value;
            try {
                value = objectMapper.readValue(decompress(bytes, clientConfig.getCompression()), JsonType.class);
            } catch (Exception e) {
                log().error("Could not parse input. Expected a Json message.", e);
                return;
            }
            if (value instanceof ResultBatch) {
                String batchId = FluxCapacitor.generateId();
                ((ResultBatch) value).getResults().forEach(r -> resultExecutor.execute(() -> handleResult(r, batchId)));
            } else {
                WebSocketRequest webSocketRequest = requests.get(((QueryResult) value).getRequestId());
                if (webSocketRequest == null) {
                    log().warn("Could not find outstanding read request for id {} (session {})",
                             ((QueryResult) value).getRequestId(), session.getId());
                }
                handleResult((QueryResult) value, null);
            }
        });

    }

    protected void handleResult(QueryResult result, String batchId) {
        try {
            WebSocketRequest webSocketRequest = requests.remove(result.getRequestId());
            if (webSocketRequest == null) {
                log().warn("Could not find outstanding read request for id {}", result.getRequestId());
            } else {
                try {
                    Metadata metadata = metricsMetadata()
                            .with("requestId", webSocketRequest.request.getRequestId(),
                                  "msDuration", currentTimeMillis() - webSocketRequest.sendTimestamp)
                            .with(webSocketRequest.correlationData)
                            .with("batchId", batchId);
                    FluxCapacitor.getOptionally().or(() -> ofNullable(webSocketRequest.fluxCapacitor))
                            .ifPresent(fc -> fc.execute(f -> ofNullable(webSocketRequest.adhocMetricsInterceptor)
                                    .ifPresentOrElse(
                                            i -> AdhocDispatchInterceptor.runWithAdhocInterceptor(
                                                    () -> tryPublishMetrics(result, metadata), i,
                                                    METRICS),
                                            () -> tryPublishMetrics(result, metadata))));
                } finally {
                    if (result instanceof ErrorResult e) {
                        webSocketRequest.result.completeExceptionally(new ServiceException(e.getMessage()));
                    } else {
                        webSocketRequest.result.complete(result);
                    }
                }
            }
        } catch (Throwable e) {
            log().error("Failed to handle result {}", result, e);
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        schedulePing(session);
    }

    protected PingRegistration schedulePing(Session session) {
        return pingDeadlines.compute(session.getId(), (k, v) -> {
            if (v != null) {
                v.cancel();
            }
            return !closed.get() ? new PingRegistration(
                    pingScheduler.schedule(clientConfig.getPingDelay(), () -> sendPing(session))) : null;
        });
    }

    @SneakyThrows
    protected void sendPing(Session session) {
        if (!closed.get()) {
            if (session.isOpen()) {
                var registration = pingDeadlines.compute(session.getId(), (k, v) -> {
                    if (v != null) {
                        v.cancel();
                    }
                    return new PingRegistration(pingScheduler.schedule(clientConfig.getPingTimeout(), () -> {
                        log().warn("Failed to get a ping response in time for session {}. Resetting connection",
                                 session.getId());
                        abort(session);
                    }));
                });
                try {
                    session.getBasicRemote().sendPing(ByteBuffer.wrap(registration.getId().getBytes()));
                } catch (Exception e) {
                    log().warn("Failed to send ping message", e);
                }
            }
        }
    }

    @SuppressWarnings("TryFinallyCanBeTryWithResources")
    protected void abort(Session session) {
        var reason = new CloseReason(NO_STATUS_CODE, null);
        try {
            onClose(session, reason);
        } finally {
            try {
                session.close(reason);
            } catch (Throwable ignored) {
            }
        }
    }

    @OnMessage
    public void onPong(PongMessage message, Session session) {
        pingDeadlines.compute(session.getId(), (k, v) -> {
            if (v == null) {
                return v;
            }
            v.cancel();
            return schedulePing(session);
        });
    }

    @Value
    protected static class PingRegistration implements Registration {
        String id = FluxCapacitor.generateId();
        @Delegate
        Registration delegate;
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        ofNullable(sessionBacklogs.remove(session.getId())).ifPresent(Backlog::shutDown);
        ofNullable(pingDeadlines.remove(session.getId())).ifPresent(PingRegistration::cancel);
        if (closeReason.getCloseCode().getCode() > NO_STATUS_CODE.getCode()) {
            log().warn("Connection to endpoint {} closed with reason {}", session.getRequestURI(), closeReason);
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
            synchronized (sessionId.intern()) {
                requests.values().stream().filter(r -> sessionId.equals(r.sessionId)).forEach(
                        r -> {
                            log().info("Retrying request {} using a new session (old session {})",
                                     r.request.getRequestId(), sessionId);
                            r.send();
                        });
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable e) {
        log().error("Client side error for web socket connected to endpoint {}", session.getRequestURI(), e);
    }

    @Override
    public void close() {
        close(false);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    protected void close(boolean clearOutstandingRequests) {
        if (closed.compareAndSet(false, true)) {
            synchronized (closed) {
                if (clearOutstandingRequests) {
                    requests.clear();
                }
                pingScheduler.shutdown();
                sessionPool.close();
                pingDeadlines.clear();
                if (!requests.isEmpty()) {
                    log().warn("{}: Closed websocket session to endpoint with {} outstanding requests",
                             getClass().getSimpleName(), requests.size());
                }
            }
        }
    }

    protected void tryPublishMetrics(JsonType message, Metadata metadata) {
        Object metric = message.toMetric();
        if (allowMetrics && !clientConfig.isDisableMetrics() && metric != null) {
            FluxCapacitor.getOptionally().ifPresentOrElse(
                    f -> publishMetrics(metric, metadata),
                    () -> client.getGatewayClient(METRICS).append(
                            STORED, asMessage(message).addMetadata(metadata).serialize(getFallbackSerializer())));
        }
    }

    protected Metadata metricsMetadata() {
        return Metadata.empty();
    }

    @RequiredArgsConstructor
    protected class WebSocketRequest {
        private final Request request;
        private final CompletableFuture<QueryResult> result = new CompletableFuture<>();
        private final Map<String, String> correlationData;
        private final DispatchInterceptor adhocMetricsInterceptor;
        private final FluxCapacitor fluxCapacitor;
        private volatile String sessionId;
        private volatile long sendTimestamp;

        @SuppressWarnings("unchecked")
        protected <T extends QueryResult> CompletableFuture<T> send() {
            Session session;
            try {
                session = request instanceof Command c ? sessionPool.get(c.routingKey()) : sessionPool.get();
            } catch (Exception e) {
                log().error("Failed to get websocket session to send request {}", request, e);
                result.completeExceptionally(e);
                return (CompletableFuture<T>) result;
            }
            this.sessionId = session.getId();
            requests.put(request.getRequestId(), this);

            try {
                sendTimestamp = System.currentTimeMillis();
                AbstractWebsocketClient.this.send(request, correlationData, session);
            } catch (Exception e) {
                requests.remove(request.getRequestId());
                result.completeExceptionally(e);
            }
            return (CompletableFuture<T>) result;
        }
    }

}
