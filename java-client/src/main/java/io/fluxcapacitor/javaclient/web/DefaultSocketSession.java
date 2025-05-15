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

package io.fluxcapacitor.javaclient.web;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.handling.Request;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static java.util.Optional.ofNullable;

@AllArgsConstructor
@Slf4j
public class DefaultSocketSession implements SocketSession {
    @Getter
    @Accessors(fluent = true)
    private final String sessionId;
    private final String target;
    @Getter
    private final String url;
    @Getter
    private final Map<String, List<String>> headers;
    private final ResultGateway webResponseGateway;
    private final BiConsumer<DefaultSocketSession, Integer> abortCallback;

    private final AtomicBoolean closed = new AtomicBoolean();

    private final Map<Long, PendingRequest<?>> pendingRequests = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> sendMessage(Object value, Guarantee guarantee) {
        return sendMessage(Message.asMessage(value).addMetadata("function", "message"), guarantee);
    }

    CompletableFuture<Void> sendMessage(Message message, Guarantee guarantee) {
        return webResponseGateway.respond(message.getPayload(), message.getMetadata().with("sessionId", sessionId),
                                          target, null, guarantee);
    }

    @Override
    public <R> CompletionStage<R> sendRequest(Request<R> request, Duration timeout) {
        SocketRequest socketRequest = SocketRequest.valueOf(request);
        CompletableFuture<R> response = new CompletableFuture<R>()
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((r, e) -> pendingRequests.remove(socketRequest.getRequestId()));
        pendingRequests.put(socketRequest.getRequestId(), new PendingRequest<>(request, response));
        if (isOpen()) {
            try {
                sendMessage(socketRequest);
            } catch (Throwable e) {
                log.error("Failed to send request {}", request, e);
                response.completeExceptionally(e);
            }
        } else {
            response.completeExceptionally(
                    new IllegalStateException("Websocket session %s is no longer open".formatted(sessionId)));
        }
        return response;
    }

    public Optional<HandlerInvoker> tryHandleRequest(DeserializingMessage message,
                                                     Handler<DeserializingMessage> handler) {
        SocketRequest request;
        try {
            request = JsonUtils.fromJson(message.getSerializedObject().getData().getValue(), SocketRequest.class);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
        if (!request.isValid()) {
            return Optional.empty();
        }
        return message.withData(new Data<>(JsonUtils.asBytes(request.getRequest()), null, 0))
                .apply(m -> handler.getInvoker(m)
                        .map(i -> new HandlerInvoker.DelegatingHandlerInvoker(i) {
                            @Override
                            public Object invoke(BiFunction<Object, Object, Object> resultCombiner) {
                                return getSocketResponse();
                            }

                            @NotNull
                            private Object getSocketResponse() {
                                try {
                                    Object result = delegate.invoke();
                                    if (result instanceof CompletableFuture<?> future) {
                                        return future.thenApply(r -> SocketResponse.success(
                                                        request.getRequestId(), JsonUtils.valueToTree(r)))
                                                .exceptionally(
                                                        e -> SocketResponse.error(request.getRequestId(), ofNullable(
                                                                ObjectUtils.unwrapException(e).getMessage()).orElse(
                                                                "Request failed")));
                                    }
                                    return SocketResponse.success(request.getRequestId(),
                                                                  JsonUtils.valueToTree(result));
                                } catch (Throwable e) {
                                    return SocketResponse.error(request.getRequestId(),
                                                                ofNullable(e.getMessage()).orElse("Request failed"));
                                }
                            }
                        }));
    }

    public Optional<HandlerInvoker> tryCompleteRequest(DeserializingMessage message) {
        SocketResponse response;
        try {
            response = message.getPayloadAs(SocketResponse.class);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
        if (!response.isValid()) {
            return Optional.empty();
        }
        return Optional.of(
                HandlerInvoker.run(() -> ofNullable(pendingRequests.remove(response.getRequestId()))
                        .ifPresentOrElse(
                                f -> {
                                    if (response.getError() != null) {
                                        f.completeExceptionally(new IllegalStateException(response.getError()));
                                        return;
                                    }
                                    try {
                                        Object result = response.deserialize(f.getRequest().responseType());
                                        f.completeSafely(result);
                                    } catch (Throwable e) {
                                        log.error("Error deserializing response for {}", response.getRequestId(), e);
                                        f.completeExceptionally(e);
                                    }
                                },
                                () -> log.warn("No outstanding request {} for response {}", response.getRequestId(),
                                               response))));
    }

    @Override
    public CompletableFuture<Void> sendPing(Object value, Guarantee guarantee) {
        return sendMessage(Message.asMessage(value).addMetadata("function", "ping"), guarantee);
    }

    @Override
    public CompletableFuture<Void> close(int code, Guarantee guarantee) {
        try {
            if (code < 1000 || code > 4999) {
                throw new IllegalArgumentException("Invalid code: " + code);
            }
            return sendMessage(Message.asMessage(String.valueOf(code)).addMetadata("function", "close"), guarantee);
        } finally {
            if (onClose()) {
                abortCallback.accept(this, code);
            }
        }
    }

    public boolean onClose() {
        if (closed.compareAndSet(false, true)) {
            pendingRequests.values()
                    .forEach(f -> f.completeExceptionally(
                            new IllegalStateException("Websocket session %s has closed".formatted(sessionId))));
            return true;
        }
        return false;
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Value
    static class PendingRequest<R> {
        Request<R> request;
        @Delegate
        CompletableFuture<R> callback;

        @SuppressWarnings("unchecked")
        public void completeSafely(Object result) {
            callback.complete((R) result);
        }
    }
}
