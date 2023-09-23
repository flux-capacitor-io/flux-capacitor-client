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

package io.fluxcapacitor.testserver;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.ClientEvent;
import io.fluxcapacitor.common.api.Command;
import io.fluxcapacitor.common.api.DisconnectEvent;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.RequestBatch;
import io.fluxcapacitor.common.api.VoidResult;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.testserver.endpoints.metrics.MetricsLog;
import io.fluxcapacitor.testserver.endpoints.metrics.NoOpMetricsLog;
import io.undertow.util.SameThreadExecutor;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.Session;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;
import static io.fluxcapacitor.common.ObjectUtils.newThreadName;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;
import static jakarta.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;
import static jakarta.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.runAsync;

@Slf4j
public abstract class WebsocketEndpoint extends Endpoint {

    private static final ObjectMapper defaultObjectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules().disable(WRITE_DATES_AS_TIMESTAMPS).build();

    private final ObjectMapper objectMapper;
    private final Executor requestExecutor;
    private final Executor responseExecutor;

    @Setter
    @Accessors(chain = true, fluent = true)
    MetricsLog metricsLog = new NoOpMetricsLog();

    private final Map<String, Session> openSessions = new ConcurrentHashMap<>();
    protected final AtomicBoolean shuttingDown = new AtomicBoolean();
    protected volatile boolean shutDown;

    protected WebsocketEndpoint() {
        this.objectMapper = defaultObjectMapper;
        this.requestExecutor = Executors.newFixedThreadPool(32, newThreadFactory(getClass().getSimpleName() + "-request"));
        this.responseExecutor = Executors.newFixedThreadPool(32, newThreadFactory(getClass().getSimpleName() + "-response"));
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown, newThreadName(getClass().getSimpleName() + "-shutdown")));
    }

    protected WebsocketEndpoint(ObjectMapper objectMapper, Executor requestExecutor, Executor responseExecutor) {
        this.objectMapper = objectMapper;
        this.requestExecutor = Optional.ofNullable(requestExecutor).orElse(SameThreadExecutor.INSTANCE);
        this.responseExecutor = Optional.ofNullable(responseExecutor).orElse(SameThreadExecutor.INSTANCE);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown, newThreadName(getClass().getSimpleName() + "-shutdown")));
    }

    private final Handler<Request> handler =
            HandlerInspector.createHandler(this, Handle.class, Arrays.asList(new ParameterResolver<>() {
                @Override
                public boolean matches(Parameter parameter, Annotation methodAnnotation, Request value, Object target) {
                    return parameter.getType().isAssignableFrom(value.payload().getClass());
                }

                @Override
                public Function<Request, Object> resolve(Parameter p, Annotation methodAnnotation) {
                    return Request::payload;
                }

                @Override
                public boolean determinesSpecificity() {
                    return true;
                }
            }, (p, methodAnnotation) -> {
                if (p.getType().equals(Session.class)) {
                    return Request::session;
                }
                return null;
            }));

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        if (shuttingDown.get()) {
            throw new IllegalStateException("Cannot accept client. Endpoint is shutting down");
        }
        openSessions.put(session.getId(), session);

        session.addMessageHandler(byte[].class, bytes -> {
            Runnable task = () -> {
                try {
                    handleMessage(session, bytes);
                } catch (Exception e) {
                    log.error("Failed to handle request", e);
                }
            };
            if (requestExecutor == null) {
                task.run();
            } else {
                runAsync(task, requestExecutor);
            }
        });
    }

    protected void handleMessage(Session session, byte[] bytes) {
        JsonType value;
        try {
            value = objectMapper.readValue(decompress(bytes, getCompressionAlgorithm(session)), getRequestType());
        } catch (IOException e2) {
            throw new IllegalArgumentException("Failed to parse incoming message as JsonType", e2);
        }
        if (shutDown) {
            throw new IllegalStateException(
                    format("Rejecting request %s from client %s with id %s because the service is shutting down",
                           value, getClientName(session), getClientId(session)));
        }
        if (shuttingDown.get()) {
            log.info("Silently ignoring request {} from client {} with id {} because the service is shutting down",
                     value, getClientName(session), getClientId(session));
            return;
        }
        handleRequest(session, value);
    }

    private void handleRequest(Session session, JsonType value) {
        if (value instanceof RequestBatch<?>) {
            ((RequestBatch<?>) value).getRequests().forEach(r -> handleRequest(session, r));
            return;
        }
        HandlerInvoker invoker = handler.findInvoker(new Request(value, session)).orElseThrow(
                () -> new IllegalArgumentException("Could not find find a handler for request " + value));
        Object result;
        try {
            result = invoker.invoke();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not handle request " + value, e);
        }
        if (result == null && value instanceof Command command && command.getGuarantee().compareTo(Guarantee.STORED) >= 0) {
            result = new VoidResult(command.getRequestId());
        }
        if (result != null) {
            sendResult(session, result);
        }
    }

    protected Class<? extends JsonType> getRequestType() {
        return JsonType.class;
    }

    protected void sendResult(Session session, Object result) {
        responseExecutor.execute(() -> {
            if (session.isOpen()) {
                try (OutputStream outputStream = session.getBasicRemote().getSendStream()) {
                    byte[] bytes = objectMapper.writeValueAsBytes(result);
                    outputStream.write(compress(bytes, getCompressionAlgorithm(session)));
                } catch (Exception e) {
                    log.error("Failed to send websocket result to client {}, id {}",
                              getClientName(session), getClientId(session), e);
                }
            }
        });
    }

    @Override
    @SuppressWarnings("resource")
    public void onClose(Session session, CloseReason closeReason) {
        openSessions.remove(session.getId());
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

    protected void registerMetrics(ClientEvent event) {
        metricsLog.registerMetrics(event);
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
                openSessions.values().stream().filter(Session::isOpen).forEach(session -> {
                    try {
                        session.close();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    protected CompressionAlgorithm getCompressionAlgorithm(Session session) {
        List<String> compression = session.getRequestParameterMap().get("compression");
        if (compression == null) {
            return CompressionAlgorithm.NONE;
        }
        return CompressionAlgorithm.valueOf(compression.get(0));
    }

    protected String getClientId(Session session) {
        return session.getRequestParameterMap().get("clientId").get(0);
    }

    protected String getClientName(Session session) {
        return session.getRequestParameterMap().get("clientName").get(0);
    }

    private record Request(JsonType payload, Session session) {
    }
}
