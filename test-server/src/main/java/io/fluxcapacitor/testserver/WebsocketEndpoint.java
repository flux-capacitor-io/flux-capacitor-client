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

package io.fluxcapacitor.testserver;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.RequestBatch;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInspector;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.undertow.util.SameThreadExecutor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;
import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.runAsync;
import static javax.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;
import static javax.websocket.CloseReason.CloseCodes.UNEXPECTED_CONDITION;

@Slf4j
public abstract class WebsocketEndpoint extends Endpoint {

    private static final ObjectMapper defaultObjectMapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules().disable(WRITE_DATES_AS_TIMESTAMPS).build();

    private final ObjectMapper objectMapper;
    private final Executor requestExecutor;
    private final Executor responseExecutor;

    private final Map<String, Session> openSessions = new ConcurrentHashMap<>();
    protected final AtomicBoolean shuttingDown = new AtomicBoolean();
    protected volatile boolean shutDown;

    protected WebsocketEndpoint() {
        this(defaultObjectMapper, Executors.newWorkStealingPool(64),
             Executors.newWorkStealingPool(8));
    }

    protected WebsocketEndpoint(Executor requestExecutor) {
        this(defaultObjectMapper, requestExecutor, Executors.newWorkStealingPool(8));
    }

    protected WebsocketEndpoint(ObjectMapper objectMapper, Executor requestExecutor,
                                Executor responseExecutor) {
        this.objectMapper = objectMapper;
        this.requestExecutor = Optional.ofNullable(requestExecutor).orElse(SameThreadExecutor.INSTANCE);
        this.responseExecutor = Optional.ofNullable(responseExecutor).orElse(SameThreadExecutor.INSTANCE);
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutDown));
    }

    private final Handler<Request> handler =
            HandlerInspector.createHandler(this, Handle.class, Arrays.asList(new ParameterResolver<>() {
                @Override
                public Function<Request, Object> resolve(Parameter p, Annotation methodAnnotation) {
                    if (Objects.equals(p.getDeclaringExecutable().getParameters()[0], p)) {
                        return Request::getPayload;
                    }
                    return null;
                }

                @Override
                public boolean determinesSpecificity() {
                    return true;
                }
            }, (p, methodAnnotation) -> {
                if (p.getType().equals(Session.class)) {
                    return Request::getSession;
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
    public void onClose(Session session, CloseReason closeReason) {
        openSessions.remove(session.getId());
        if (closeReason.getCloseCode() != UNEXPECTED_CONDITION
                && closeReason.getCloseCode().getCode() > NO_STATUS_CODE.getCode()) {
            log.warn("Websocket session for client {} with id {} closed abnormally: {}", getClientName(session),
                     getClientId(session), closeReason);
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
     * Close all sessions on the websocket after an optional delay. During the delay we don't handle new requests
     * but will be able to send back results.
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
            return null;
        }
        return CompressionAlgorithm.valueOf(compression.get(0));
    }

    protected String getProjectId(Session session) {
        return Optional.ofNullable(session.getRequestParameterMap().get("projectId")).map(list -> list.get(0))
                .orElse("public");
    }

    protected String getClientId(Session session) {
        return session.getRequestParameterMap().get("clientId").get(0);
    }

    protected String getClientName(Session session) {
        return session.getRequestParameterMap().get("clientName").get(0);
    }

    @Value
    private static class Request {
        JsonType payload;
        Session session;
    }
}
