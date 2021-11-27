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
import io.fluxcapacitor.common.api.JsonType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.QueryResult;
import io.fluxcapacitor.common.api.Request;
import io.fluxcapacitor.common.api.RequestBatch;
import io.fluxcapacitor.common.api.ResultBatch;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.compress;
import static io.fluxcapacitor.common.serialization.compression.CompressionUtils.decompress;
import static io.fluxcapacitor.javaclient.FluxCapacitor.currentCorrelationData;
import static io.fluxcapacitor.javaclient.FluxCapacitor.publishMetrics;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.nio.ByteBuffer.wrap;
import static java.util.stream.Collectors.toList;

@Slf4j
public abstract class AbstractWebsocketClient implements Listener, AutoCloseable {
    public static ObjectMapper defaultObjectMapper = JsonMapper.builder().disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .findAndAddModules().disable(WRITE_DATES_AS_TIMESTAMPS).build();

    private final AtomicBoolean closed = new AtomicBoolean();
    private final WebSocketSupplier webSocketSupplier;
    private final URI uri;
    private final ClientConfig clientConfig;
    private final ObjectMapper objectMapper;
    private final Function<WebSocket, Backlog<JsonType>> socketBacklogs =
            memoize(s -> new Backlog<>(batch -> sendBatch(batch, s)));
    private final Map<Long, WebSocketRequest> requests = new ConcurrentHashMap<>();
    private final ExecutorService resultExecutor = Executors.newFixedThreadPool(8);
    private final ByteArrayOutputStream messageByteStream = new ByteArrayOutputStream();
    private final boolean sendMetrics;

    public AbstractWebsocketClient(URI uri, ClientConfig clientConfig, boolean sendMetrics) {
        this(uri, clientConfig, sendMetrics, 1);
    }

    public AbstractWebsocketClient(URI uri, ClientConfig clientConfig, boolean sendMetrics,
                                   int numberOfSessions) {
        this(uri, clientConfig, sendMetrics, defaultObjectMapper, Duration.ofSeconds(5), Duration.ofSeconds(1),
             numberOfSessions);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @SneakyThrows
    public AbstractWebsocketClient(URI uri, ClientConfig clientConfig, boolean sendMetrics,
                                   ObjectMapper objectMapper, Duration connectTimeout, Duration reconnectDelay,
                                   int numberOfSessions) {
        this.uri = uri;
        this.clientConfig = clientConfig;
        this.objectMapper = objectMapper;
        this.sendMetrics = sendMetrics;
        this.webSocketSupplier = (WebSocketSupplier) Optional.of(
                        RetryingWebSocket.builder().reconnectDelay(reconnectDelay).connectTimeout(connectTimeout))
                .map(b -> numberOfSessions == 1 ? b : WebSocketPool.builder(b).sessionCount(numberOfSessions))
                .map(b -> b.buildAsync(uri, this)).get().get();
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
        return socketBacklogs.apply(webSocketSupplier.get()).add(object);
    }

    @SneakyThrows
    protected Awaitable sendBatch(List<JsonType> requests, WebSocket webSocket) {
        Metadata metadata = requests.size() > 1 ? Metadata.of("batchId", FluxCapacitor.generateId()) : Metadata.empty();
        Collection<WebSocketRequest> webSocketRequests = new ArrayList<>();
        requests.forEach(r -> {
            if (r instanceof Request) {
                WebSocketRequest webSocketRequest = this.requests.get(((Request) r).getRequestId());
                if (webSocketRequest != null) {
                    webSocketRequests.add(webSocketRequest);
                }
            }
            tryPublishMetrics(r, r instanceof Request
                    ? metadata.with("requestId", ((Request) r).getRequestId()) : metadata);
        });
        JsonType object = requests.size() == 1 ? requests.get(0) : new RequestBatch<>(requests);
        byte[] bytes;
        try {
            bytes = compress(objectMapper.writeValueAsBytes(object), clientConfig.getCompression());
        } catch (Exception e) {
            log.error("Failed to serialize request {}", object, e);
            webSocketRequests.forEach(r -> r.result.completeExceptionally(e));
            return Awaitable.failed(e);
        }
        try {
            webSocket.sendBinary(wrap(bytes), true).get();
            return Awaitable.ready();
        } catch (Exception e) {
            log.error("Permanently failed to send request {}", object, e);
            webSocketRequests.forEach(r -> r.result.completeExceptionally(e));
            return Awaitable.failed(e);
        }
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        byte[] arr = new byte[data.remaining()];
        data.get(arr);
        messageByteStream.writeBytes(arr);
        webSocket.request(1);
        if (last) {
            onMessage(messageByteStream.toByteArray());
            messageByteStream.reset();
        }
        return null;
    }

    @SneakyThrows
    protected void onMessage(byte[] bytes) {
        resultExecutor.execute(() -> {
            JsonType value;
            try {
                value = objectMapper.readValue(decompress(bytes, clientConfig.getCompression()), JsonType.class);
            } catch (Exception e) {
                log.error("Could not parse input. Expected a Json message.", e);
                return;
            }
            if (value instanceof ResultBatch) {
                String batchId = FluxCapacitor.generateId();
                ((ResultBatch) value).getResults().forEach(r -> resultExecutor.execute(() -> handleResult(r, batchId)));
            } else {
                handleResult((QueryResult) value, null);
            }
        });
    }

    protected void handleResult(QueryResult result, String batchId) {
        try {
            WebSocketRequest webSocketRequest = requests.remove(result.getRequestId());
            if (webSocketRequest == null) {
                log.warn("Could not find outstanding read request for id {}", result.getRequestId());
            } else {
                try {
                    Metadata metadata = Metadata.of("requestId", webSocketRequest.request.getRequestId(),
                                                    "msDuration", currentTimeMillis() - webSocketRequest.sendTimestamp)
                            .with(webSocketRequest.correlationData);
                    tryPublishMetrics(result, batchId == null ? metadata : metadata.with("batchId", batchId));
                } finally {
                    webSocketRequest.result.complete(result);
                }
            }
        } catch (Throwable e) {
            log.error("Failed to handle result {}", result, e);
        }
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("Client side error for web socket connected to endpoint {}", uri, error);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        if (!closed.get()) {
            log.warn("Connection to endpoint {} closed with reason {} (status {})", uri, reason, statusCode);
        }
        messageByteStream.reset();
        retryOutstandingRequests(webSocket);
        return null;
    }

    protected void retryOutstandingRequests(WebSocket webSocket) {
        Collection<WebSocketRequest> requests =
                this.requests.values().stream().filter(r -> webSocket.equals(r.sender)).collect(toList());
        if (!closed.get() && !requests.isEmpty()) {
            try {
                sleep(1_000);
            } catch (InterruptedException e) {
                currentThread().interrupt();
                throw new IllegalStateException("Thread interrupted while trying to retry outstanding requests", e);
            }
            requests.forEach(WebSocketRequest::send);
        }
    }

    @Override
    @SneakyThrows
    public void close() {
        close(false);
    }

    @SneakyThrows
    protected void close(boolean clearOutstandingRequests) {
        if (closed.compareAndSet(false, true)) {
            synchronized (closed) {
                if (clearOutstandingRequests) {
                    requests.clear();
                }
                webSocketSupplier.close();
                if (!requests.isEmpty()) {
                    log.warn("{}: Closed websocket session to endpoint with {} outstanding requests",
                             getClass().getSimpleName(), requests.size());
                }
            }
        }
    }

    protected void tryPublishMetrics(JsonType message, Metadata metadata) {
        Object metric = message.toMetric();
        if (sendMetrics && metric != null) {
            FluxCapacitor.getOptionally().ifPresent(f -> publishMetrics(metric, metadata));
        }
    }

    @RequiredArgsConstructor
    protected class WebSocketRequest {
        private final Request request;
        private final CompletableFuture<QueryResult> result = new CompletableFuture<>();
        private final Map<String, String> correlationData;
        private final WebSocket sender = webSocketSupplier.get();

        private volatile long sendTimestamp;

        @SuppressWarnings("unchecked")
        protected <T extends QueryResult> CompletableFuture<T> send() {
            requests.put(request.getRequestId(), this);
            try {
                sendTimestamp = System.currentTimeMillis();
                socketBacklogs.apply(sender).add(request);
            } catch (Exception e) {
                requests.remove(request.getRequestId());
                result.completeExceptionally(e);
            }
            return (CompletableFuture<T>) result;
        }
    }

}
