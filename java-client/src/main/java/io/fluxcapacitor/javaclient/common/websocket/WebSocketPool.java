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

import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.asSupplier;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

@Slf4j
public class WebSocketPool implements WebSocket, WebSocketSupplier {

    public static Builder builder(WebSocket.Builder delegate) {
        return new Builder(delegate);
    }

    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger counter = new AtomicInteger();

    private final Supplier<WebSocket> socketFactory;
    private final List<AtomicReference<WebSocket>> sockets;

    protected WebSocketPool(WebSocket.Builder builder, int sessionCount, URI uri, Listener listener) {
        this.socketFactory = asSupplier(() -> builder.buildAsync(uri, new SocketListener(listener)).get());
        this.sockets = range(0, sessionCount).mapToObj(i -> new AtomicReference<WebSocket>()).collect(toList());
    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
        return get().sendText(data, last);
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
        return get().sendBinary(data, last);
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        return get().sendPing(message);
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        return get().sendPong(message);
    }

    @Override
    public void request(long n) {
        get().request(n);
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
        return isClosed();
    }

    @Override
    public boolean isInputClosed() {
        return isClosed();
    }

    @Override
    public String getSubprotocol() {
        return get().getSubprotocol();
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public WebSocket get() {
        AtomicReference<WebSocket> socket =
                sockets.get(counter.getAndAccumulate(1, (i, inc) -> {
                    int newIndex = i + inc;
                    return newIndex >= sockets.size() ? 0 : newIndex;
                }));
        return socket.updateAndGet(s -> s == null ? socketFactory.get() : s);
    }

    @AllArgsConstructor
    protected static class SocketListener implements Listener {
        private final Listener delegate;

        private final ByteArrayOutputStream messageByteStream = new ByteArrayOutputStream();
        private final StringBuilder stringBuilder = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            stringBuilder.append(data);
            if (last) {
                String string = stringBuilder.toString();
                stringBuilder.setLength(0);
                synchronized (delegate) {
                    return delegate.onText(webSocket, string, true);
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
                synchronized (delegate) {
                    return delegate.onBinary(webSocket, byteBuffer, true);
                }
            } else {
                webSocket.request(1);
                return null;
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            delegate.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
            return delegate.onPing(webSocket, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
            return delegate.onPong(webSocket, message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return delegate.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            delegate.onError(webSocket, error);
        }
    }


    @Setter
    @Accessors(chain = true, fluent = true)
    protected static class Builder implements WebSocket.Builder {

        private WebSocket.Builder delegate;
        private int sessionCount = 1;

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
            return new WebSocketPool(delegate, sessionCount, uri, listener);
        }
    }
}
