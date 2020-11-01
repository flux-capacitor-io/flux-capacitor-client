/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.RetryConfiguration;
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.Request;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.Properties;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DecodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static io.fluxcapacitor.javaclient.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.javaclient.common.serialization.compression.CompressionUtils.decompress;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static javax.websocket.CloseReason.CloseCodes.NO_STATUS_CODE;

@Slf4j
public abstract class AbstractWebsocketClient implements AutoCloseable {
    public static final ObjectMapper defaultObjectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final WebSocketContainer container;
    private final URI endpointUri;
    private final Properties properties;
    private final ObjectMapper objectMapper;
    private final Map<Long, WebSocketRequest> requests = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final RetryConfiguration retryConfig;
    private volatile Session session;

    public AbstractWebsocketClient(URI endpointUri, Properties properties) {
        this(ContainerProvider.getWebSocketContainer(), endpointUri, properties, Duration.ofSeconds(1),
             defaultObjectMapper);
    }

    public AbstractWebsocketClient(WebSocketContainer container, URI endpointUri,
                                   Properties properties, Duration reconnectDelay, ObjectMapper objectMapper) {
        this.container = container;
        this.endpointUri = endpointUri;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.retryConfig = RetryConfiguration.builder()
                .delay(reconnectDelay)
                .errorTest(e -> !closed.get())
                .successLogger(s -> log.info("Successfully reconnected to endpoint {}", endpointUri))
                .exceptionLogger(status -> {
                    if (status.getNumberOfTimesRetried() == 0) {
                        log.warn("Failed to connect to endpoint {}; reason: {}. Retrying every {} ms...",
                                 endpointUri, status.getException().getMessage(),
                                 status.getRetryConfiguration().getDelay().toMillis());
                    }
                })
                .build();
    }

    @SneakyThrows
    protected Awaitable send(Object object) {
        return send(object, getSession());
    }

    @SneakyThrows
    protected Awaitable send(Object object, Session session) {
        try (OutputStream outputStream = session.getBasicRemote().getSendStream()) {
            byte[] bytes = objectMapper.writeValueAsBytes(object);
            outputStream.write(compress(bytes, properties.getCompression()));
        }
        return Awaitable.ready();
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    protected <R extends QueryResult> R sendRequestAndWait(Request request) {
        return (R) sendRequest(request).get();
    }

    @SuppressWarnings("unchecked")
    protected <R extends QueryResult> CompletableFuture<R> sendRequest(Request request) {
        WebSocketRequest webSocketRequest = new WebSocketRequest(request);
        webSocketRequest.send();
        return (CompletableFuture<R>) webSocketRequest.result;
    }

    @OnMessage
    @SneakyThrows
    public void onMessage(byte[] bytes) {
        JsonType value;
        try {
            value = objectMapper.readValue(decompress(bytes, properties.getCompression()), JsonType.class);
        } catch (Exception e) {
            throw new DecodeException("", "Could not parse input. Expected a Json message.", e);
        }
        QueryResult readResult = (QueryResult) value;
        WebSocketRequest webSocketRequest = requests.remove(readResult.getRequestId());
        if (webSocketRequest == null) {
            log.warn("Could not find outstanding read request for id {}", readResult.getRequestId());
        } else {
            webSocketRequest.result.complete(readResult);
//            ForkJoinPool.commonPool().execute(() -> webSocketRequest.result.complete(readResult));
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        if (closeReason.getCloseCode().getCode() > NO_STATUS_CODE.getCode()) {
            log.warn("Connection to endpoint {} closed with reason {}", session.getRequestURI(), closeReason);
        }
        retryOutstandingRequests(session.getId());
    }

    protected void retryOutstandingRequests(String sessionId) {
        if (!closed.get() && !requests.isEmpty()) {
            try {
                sleep(retryConfig.getDelay().toMillis());
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
                if (session != null) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        log.warn("Failed to closed websocket session connected to endpoint {}. Reason: {}",
                                 session.getRequestURI(), e.getMessage());
                    }
                }
                if (session != null && !requests.isEmpty()) {
                    log.warn("Closed websocket session to endpoint {} with {} outstanding requests",
                             session.getRequestURI(), requests.size());
                }
            }
        }
    }

    protected Session getSession() {
        if (isClosed(session)) {
            synchronized (closed) {
                while (isClosed(session)) {
                    if (closed.get()) {
                        throw new IllegalStateException("Cannot provide session. This client has closed");
                    }
                    log.info("Getting session for {}", this.getClass());
                    session = retryOnFailure(() -> isClosed(session) ?
                            container.connectToServer(this, endpointUri) : session, retryConfig);
                    log.info("Got session for {}", this.getClass());
                }
            }
        }
        return session;
    }

    protected boolean isClosed(Session session) {
        return session == null || !session.isOpen();
    }

    @RequiredArgsConstructor
    protected class WebSocketRequest {
        private final Request request;
        private final CompletableFuture<QueryResult> result = new CompletableFuture<>();
        private volatile String sessionId;

        protected void send() {
            try {
                Session session = getSession();
            } catch (Exception e) {
                log.error("Failed to get websocket session to send request {}", request, e);
                result.completeExceptionally(e);
                return;
            }
            this.sessionId = session.getId();
            requests.put(request.getRequestId(), this);
            try {
                AbstractWebsocketClient.this.send(request, session);
            } catch (Exception e) {
                requests.remove(request.getRequestId());
                log.error("Failed to send request {}", request, e);
                result.completeExceptionally(e);
            }
        }
    }

}
