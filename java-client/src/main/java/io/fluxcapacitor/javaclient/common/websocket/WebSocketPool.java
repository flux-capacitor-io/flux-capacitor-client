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
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static io.fluxcapacitor.common.TimingUtils.retryOnFailure;
import static java.util.stream.Collectors.toCollection;

@Slf4j
public class WebSocketPool implements WebSocket, AutoCloseable {

    public static Builder builder(WebSocket.Builder delegate) {
        return new Builder(delegate);
    }

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger counter = new AtomicInteger();

    private final Listener listener;
    private final Supplier<WebSocket> socketFactory;
    private final List<AtomicReference<WebSocket>> sockets;

    protected WebSocketPool(Builder builder, URI uri, Listener listener) {
        this.listener = listener;
        this.socketFactory = () -> retryOnFailure(
                () -> builder.delegate.buildAsync(uri, new SocketListener()).get(),
                RetryConfiguration.builder()
                        .delay(builder.reconnectDelay)
                        .errorTest(e -> !closed.get())
                        .successLogger(s -> log.info("Successfully reconnected to endpoint {}", uri))
                        .exceptionLogger(status -> {
                            if (status.getNumberOfTimesRetried() == 0) {
                                log.warn("Failed to connect to endpoint {}; reason: {}. Retrying every {} ms...",
                                         uri, status.getException().getMessage(),
                                         status.getRetryConfiguration().getDelay().toMillis());
                            }
                        }).build());
        this.sockets = IntStream.range(0, builder.sessionCount).mapToObj(i -> new AtomicReference<WebSocket>()).collect(
                toCollection(ArrayList::new));
    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
        return getSocket().sendText(data, last);
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
        return getSocket().sendBinary(data, last);
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        return getSocket().sendPing(message);
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        return getSocket().sendPong(message);
    }

    @Override
    public void request(long n) {
        getSocket().request(n);
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
        if (closed.compareAndSet(false, true)) {
            return CompletableFuture.allOf(sockets.stream().map(AtomicReference::get).filter(Objects::nonNull)
                                                   .map(s -> s.sendClose(statusCode, reason))
                                                   .toArray(CompletableFuture[]::new)).thenApply(s -> this);
        }
        return CompletableFuture.completedFuture(this);
    }

    @Override
    public void close() {
        abort();
    }

    @Override
    public void abort() {
        sendClose(1000, "Client is going away");
    }

    @Override
    public boolean isOutputClosed() {
        return closed.get();
    }

    @Override
    public boolean isInputClosed() {
        return closed.get();
    }

    @Override
    public String getSubprotocol() {
        return getSocket().getSubprotocol();
    }

    protected WebSocket getSocket() {
        AtomicReference<WebSocket> socket =
                sockets.get(counter.getAndAccumulate(1, (i, inc) -> {
                    int newIndex = i + inc;
                    return newIndex >= sockets.size() ? 0 : newIndex;
                }));
        return socket.updateAndGet(s -> {
            if (isClosed(s)) {
                synchronized (closed) {
                    while (isClosed(s)) {
                        if (closed.get()) {
                            throw new IllegalStateException("Cannot provide session. This client has closed");
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

    @AllArgsConstructor
    protected class SocketListener implements Listener {
        private final ByteArrayOutputStream messageByteStream = new ByteArrayOutputStream();
        private final StringBuilder stringBuilder = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            stringBuilder.append(data);
            if (last) {
                String string = stringBuilder.toString();
                stringBuilder.setLength(0);
                synchronized (listener) {
                    return listener.onText(webSocket, string, true);
                }
            } else {
                webSocket.request(1);
                return null;
            }
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] arr = new byte[data.remaining()];
            data.get(arr);
            messageByteStream.writeBytes(arr);
            if (last) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(messageByteStream.toByteArray());
                messageByteStream.reset();
                synchronized (listener) {
                    return listener.onBinary(webSocket, byteBuffer, true);
                }
            } else {
                webSocket.request(1);
                return null;
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            listener.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            return listener.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            return listener.onPong(webSocket, message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return listener.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            listener.onError(webSocket, error);
        }
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
            return new WebSocketPool(this, uri, listener);
        }
    }
}
