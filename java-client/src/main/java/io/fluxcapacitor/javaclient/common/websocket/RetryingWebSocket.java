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

import io.fluxcapacitor.common.RetryConfiguration;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.forceThrow;
import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static java.net.http.HttpClient.newHttpClient;

/**
 * Implementation of a {@link WebSocket} that reconnects if the connection is closed by the server. If it fails to send
 * data it will retry sending until the socket is closed by the client.
 * <p>
 * This Websocket implementation is thread safe. That is, you can safely send data even if a previous data transfer is
 * still pending. Note however that sending a binary message while the previous message was a non-final text message (or
 * vice versa) will still result in an {@link IllegalStateException}.
 */
@Slf4j
public class RetryingWebSocket implements WebSocket, WebSocketSupplier {
    public static HttpClient httpClient = newHttpClient();

    public static Builder builder() {
        return new RetryingWebSocket.Builder(httpClient.newWebSocketBuilder());
    }

    public static Builder builder(WebSocket.Builder delegate) {
        return new RetryingWebSocket.Builder(delegate);
    }

    private final Supplier<WebSocket> socketFactory;
    private final Duration reconnectDelay;
    private final URI uri;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final AtomicReference<WebSocket> delegate = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    protected RetryingWebSocket(WebSocket.Builder socketBuilder, Duration reconnectDelay, URI uri, Listener listener) {
        this.reconnectDelay = reconnectDelay;
        this.uri = uri;
        this.socketFactory = () -> retryOnFailure(
                () -> socketBuilder.buildAsync(uri, listener).get(),
                RetryConfiguration.builder()
                        .delay(reconnectDelay)
                        .errorTest(e -> !closed.get())
                        .successLogger(s -> log.info("Successfully reconnected to endpoint {}", uri))
                        .exceptionLogger(status -> {
                            if (status.getNumberOfTimesRetried() == 0) {
                                log.warn("Failed to connect to endpoint {}; reason: {}. Retrying every {} ms...",
                                         uri, status.getException().getMessage(),
                                         status.getRetryConfiguration().getDelay().toMillis());
                            }
                        }).build());
    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
        return sendSafely(s -> s.sendText(data, last));
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
        return sendSafely(s -> s.sendBinary(data, last));
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        return sendSafely(s -> s.sendPing(message));
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        return sendSafely(s -> s.sendPong(message));
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
        if (closed.compareAndSet(false, true)) {
            return sendSafely(s -> s.sendClose(statusCode, reason));
        }
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public void request(long n) {
        getSocket().request(n);
    }

    @Override
    public String getSubprotocol() {
        return getSocket().getSubprotocol();
    }

    @Override
    public boolean isOutputClosed() {
        return isClosed();
    }

    @Override
    public boolean isInputClosed() {
        return isClosed();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void abort() {
        sendClose(1000, "Client is going away");
    }

    @Override
    public void close() {
        abort();
    }

    @Override
    public WebSocket get() {
        return this;
    }

    protected CompletableFuture<WebSocket> sendSafely(Function<WebSocket, CompletableFuture<?>> action) {
        return trySend(s -> {
            try {
                return action.apply(s).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Got interrupted while sending websocket message. Endpoint: {}", uri);
            } catch (ExecutionException e) {
                forceThrow(e.getCause());
            }
            return null;
        });
    }

    protected CompletableFuture<WebSocket> trySend(Function<WebSocket, ?> action) {
        return CompletableFuture.supplyAsync(() -> {
            WebSocket socket = getSocket(); // if this fails it means the client is going away (and that's ok)
            retryOnFailure(() -> action.apply(socket),
                           RetryConfiguration.builder()
                                   .delay(reconnectDelay)
                                   .errorTest(e -> !isClosed())
                                   .successLogger(s -> log.info("Successfully send data to endpoint {} on retry", uri))
                                   .exceptionLogger(status -> {
                                       if (status.getNumberOfTimesRetried() == 0) {
                                           log.error("Failed to send data to endpoint {}. Retrying every {} ms...",
                                                     uri, status.getRetryConfiguration().getDelay().toMillis(),
                                                     status.getException());
                                       }
                                   }).build());
            return this;
        }, this.executor);
    }

    protected WebSocket getSocket() {
        return delegate.updateAndGet(s -> {
            if (isClosed(s)) {
                synchronized (closed) {
                    while (isClosed(s)) {
                        if (closed.get()) {
                            throw new IllegalStateException(
                                    "Cannot provide session because client has closed. Endpoint: " + uri);
                        }
                        s = socketFactory.get();
                    }
                }
            }
            return s;
        });
    }

    protected boolean isClosed(WebSocket socket) {
        return socket == null || socket.isInputClosed();
    }

    @Setter
    @Accessors(chain = true, fluent = true)
    protected static class Builder implements WebSocket.Builder {

        private WebSocket.Builder delegate;
        private int sessionCount = 1;
        private Duration reconnectDelay = Duration.ofSeconds(1);

        public Builder(WebSocket.Builder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Builder header(String name, String value) {
            delegate.header(name, value);
            return this;
        }

        @Override
        public Builder connectTimeout(Duration timeout) {
            delegate.connectTimeout(timeout);
            return this;
        }

        @Override
        public Builder subprotocols(String mostPreferred, String... lesserPreferred) {
            delegate.subprotocols(mostPreferred, lesserPreferred);
            return this;
        }

        @Override
        public CompletableFuture<WebSocket> buildAsync(URI uri, Listener listener) {
            return CompletableFuture.completedFuture(build(uri, listener));
        }

        public WebSocket build(URI uri, Listener listener) {
            return new RetryingWebSocket(delegate, reconnectDelay, uri, listener);
        }
    }
}
