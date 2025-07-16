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

package io.fluxcapacitor.testserver.websocket;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.api.BooleanResult;
import io.fluxcapacitor.common.api.Command;
import io.fluxcapacitor.common.api.ConnectEvent;
import io.fluxcapacitor.common.api.DisconnectEvent;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.Request;
import io.fluxcapacitor.common.api.RequestBatch;
import io.fluxcapacitor.common.api.RequestResult;
import io.fluxcapacitor.common.api.ResultBatch;
import io.fluxcapacitor.common.api.StringResult;
import io.fluxcapacitor.common.api.VoidResult;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.testserver.metrics.MetricsLog;
import io.fluxcapacitor.testserver.metrics.NoOpMetricsLog;
import io.undertow.util.SameThreadExecutor;
import jakarta.annotation.Nullable;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.fluxcapacitor.common.Guarantee.STORED;
import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.common.ObjectUtils.newThreadName;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;
import static jakarta.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;
import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static java.lang.String.format;

@Slf4j
public abstract class WebsocketEndpoint extends Endpoint {

    private static final ObjectMapper defaultObjectMapper = JsonMapper.builder()
            .findAndAddModules().disable(WRITE_DATES_AS_TIMESTAMPS)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES).disable(FAIL_ON_EMPTY_BEANS)
            .addModule(new NullCollectionsAsEmptyModule()).enable(ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

    @Setter
    @Accessors(chain = true, fluent = true)
    MetricsLog metricsLog = new NoOpMetricsLog();

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper objectMapper;
    private final Executor requestExecutor;

    private final Map<String, SessionBacklog> sessionBacklogs = new ConcurrentHashMap<>();
    protected final AtomicBoolean shuttingDown = new AtomicBoolean();
    protected volatile boolean shutDown;

    protected WebsocketEndpoint() {
        this.objectMapper = defaultObjectMapper;
        this.requestExecutor = Executors.newFixedThreadPool(64, newThreadFactory(getClass().getSimpleName()));
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown, newThreadName(getClass().getSimpleName() + "-shutdown")));
    }

    protected WebsocketEndpoint(@Nullable Executor requestExecutor) {
        this.objectMapper = defaultObjectMapper;
        this.requestExecutor = Optional.ofNullable(requestExecutor).orElse(SameThreadExecutor.INSTANCE);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown, newThreadName(getClass().getSimpleName() + "-shutdown")));
    }

    private final Handler<ClientMessage> handler =
            HandlerInspector.createHandler(this, Handle.class, Arrays.asList(new ParameterResolver<>() {
                @Override
                public Function<ClientMessage, Object> resolve(Parameter p, Annotation a) {
                    if (Objects.equals(p.getDeclaringExecutable().getParameters()[0], p)) {
                        return ClientMessage::getPayload;
                    }
                    return null;
                }

                @Override
                public boolean determinesSpecificity() {
                    return true;
                }
            }, (p, a) -> {
                if (p.getType().equals(Session.class)) {
                    return ClientMessage::getSession;
                }
                return null;
            }));

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("Cannot accept client. Endpoint is shutting down");
        }
        sessionBacklogs.put(session.getId(), new SessionBacklog(
                Backlog.forConsumer(results -> sendResultBatch(session, results)), session));

        session.addMessageHandler(byte[].class, bytes -> {
            Runnable task = () -> {
                try {
                    JsonType request = deserializeRequest(session, bytes);
                    if (shutDown) {
                        throw new IllegalStateException(
                                format("Rejecting request %s from client %s with id %s because the service is shutting down",
                                       request, getClientName(session), getClientId(session)));
                    }
                    if (shuttingDown.get()) {
                        log.info(
                                "Silently ignoring request {} from client {} with id {} because the service is shutting down",
                                request, getClientName(session), getClientId(session));
                        return;
                    }
                    handleMessage(session, request);
                } catch (Throwable e) {
                    log.error("Failed to handle request", e);
                }
            };
            requestExecutor.execute(task);
        });
        registerMetrics(new ConnectEvent(getClientName(session), getClientId(session), session.getId(), toString()));
    }

    @SneakyThrows
    protected JsonType deserializeRequest(Session session, byte[] bytes) {
        return objectMapper.readValue(decompress(bytes, getCompressionAlgorithm(session)), JsonType.class);
    }

    protected void handleMessage(Session session, JsonType message) {
        if (message instanceof RequestBatch<?> batch) {
            createTasks(batch, session).forEach(requestExecutor::execute);
        } else {
            try {
                Object result = handler.getInvoker(new ClientMessage(message, session)).orElseThrow().invoke();
                trySendResult(session, message, result);
            } catch (Throwable e) {
                log.error("Could not handle request {}", message, e);
            }
        }
    }

    private void trySendResult(Session session, JsonType message, Object result) {
        if (message instanceof Request request && (!(request instanceof Command command)
                                                   || command.getGuarantee().compareTo(STORED) >= 0)) {
            if (result instanceof RequestResult response) {
                doSendResult(session, response);
            } else if (result == null) {
                if (request instanceof Command) {
                    doSendResult(session, new VoidResult(request.getRequestId()));
                }
            } else if (result instanceof Boolean v) {
                doSendResult(session, new BooleanResult(request.getRequestId(), v));
            } else if (result instanceof String v) {
                doSendResult(session, new StringResult(request.getRequestId(), v));
            } else if (result instanceof CompletableFuture<?> future) {
                future.whenComplete((r, e) -> {
                    if (e != null) {
                        log.error("Request {} failed. Not sending back result to client.", message, e);
                    } else {
                        trySendResult(session, message, r);
                    }
                });
            } else {
                log.warn("Not able to send back result of type {} to client. Contents: {}. Request: {}",
                         result.getClass(), result, request);
            }
        }
    }

    protected void doSendResult(Session session, RequestResult result) {
        Optional.ofNullable(sessionBacklogs.get(session.getId())).or(() -> findAlternativeBacklog(session))
                .ifPresentOrElse(backlog -> backlog.add(result), () ->
                        log.info("Not sending result {}. Could not find any suitable sessions for client {}.",
                                 result, getClientId(session)));
    }

    protected Stream<Runnable> createTasks(RequestBatch<?> batch, Session session) {
        return batch.getRequests().stream().map(r -> () -> handleMessage(session, r));
    }

    protected void sendResultBatch(Session session, List<RequestResult> results) {
        try {
            var result = results.size() == 1 ? results.getFirst() : new ResultBatch(results);
            if (session.isOpen()) {
                try (OutputStream outputStream = session.getBasicRemote().getSendStream()) {
                    byte[] bytes = objectMapper.writeValueAsBytes(result);
                    outputStream.write(compress(bytes, getCompressionAlgorithm(session)));
                } catch (Exception e) {
                    log.error("Failed to send websocket result to client {}, id {}",
                              getClientName(session), getClientId(session), e);
                }
            } else {
                findAlternativeBacklog(session).ifPresentOrElse(b -> b.add(results), ()
                        -> log.info("Not sending batch of {}. Could not find any suitable sessions for client {}.",
                                    results.size(), getClientId(session)));
            }
        } catch (Throwable e) {
            log.error("Failed to send websocket result to client {}, id {}",
                      getClientName(session), getClientId(session), e);
            throw e;
        }
    }

    protected Optional<SessionBacklog> findAlternativeBacklog(Session closedSession) {
        String clientId = getClientId(closedSession);
        return sessionBacklogs.values().stream()
                .filter(b -> clientId.equals(getClientId(b.getSession())) && !closedSession.getId()
                        .equals(b.getSession().getId())).findFirst();
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        sessionBacklogs.remove(session.getId());
        if (!shuttingDown.get()) {
            if (closeReason.getCloseCode() != UNEXPECTED_CONDITION
                    && closeReason.getCloseCode().getCode() > NO_STATUS_CODE.getCode()) {
                log.warn("Websocket session to endpoint {} for client {} with id {} closed abnormally: {}",
                         getClass().getSimpleName(), getClientName(session), getClientId(session), closeReason);
            }
            registerMetrics(new DisconnectEvent(
                    getClientName(session), getClientId(session), session.getId(), toString(),
                    closeReason.getCloseCode().getCode(), closeReason.getReasonPhrase()));
        }
    }

    @Override
    public void onError(Session session, Throwable e) {
        log.error("Error in session for client {} with id {}", getClientName(session), getClientId(session), e);
        try {
            session.close(new CloseReason(UNEXPECTED_CONDITION, "The websocket closed because of an error"));
        } catch (IOException ignored) {
        }
    }

    /**
     * Close all sessions on the websocket after an optional delay. During the delay we don't handle new requests but
     * will be able to send back results.
     */
    protected void shutDown() {
        if (shuttingDown.compareAndSet(false, true)) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                shutDown = true;
                sessionBacklogs.values().stream().map(SessionBacklog::getSession).filter(Session::isOpen).forEach(s -> {
                    try {
                        s.close();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    protected CompressionAlgorithm getCompressionAlgorithm(Session session) {
        List<String> compression = session.getRequestParameterMap().get("compression");
        if (compression == null) {
            return null;
        }
        return CompressionAlgorithm.valueOf(compression.getFirst());
    }

    protected String getProjectId(Session session) {
        return Optional.ofNullable(session.getRequestParameterMap().get("projectId")).map(List::getFirst)
                .orElse("public");
    }

    protected String getClientId(Session session) {
        return session.getRequestParameterMap().get("clientId").getFirst();
    }

    protected String getClientName(Session session) {
        return session.getRequestParameterMap().get("clientName").getFirst();
    }

    protected void registerMetrics(JsonType event) {
        metricsLog.registerMetrics(event);
    }

    @Value
    protected static class ClientMessage {
        JsonType payload;
        Session session;
    }

    @Value
    protected static class SessionBacklog {
        @Delegate
        Backlog<RequestResult> delegate;
        Session session;
    }
}
