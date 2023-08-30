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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import io.fluxcapacitor.javaclient.web.WebResponse;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.fluxcapacitor.common.Guarantee.SENT;
import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static java.lang.String.format;

@AllArgsConstructor
@Slf4j
public class DefaultGenericGateway implements GenericGateway {
    private final GatewayClient gatewayClient;
    private final RequestHandler requestHandler;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final MessageType messageType;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    private final Map<String, CompletableFuture<?>> callbacks = new ConcurrentHashMap<>();

    @Override
    @SneakyThrows
    public CompletableFuture<Void> sendAndForget(Guarantee guarantee, Message... messages) {
        List<SerializedMessage> serializedMessages = new ArrayList<>();
        for (Message message : messages) {
            message = dispatchInterceptor.interceptDispatch(message, messageType);
            if (message == null) {
                continue;
            }
            Optional<CompletableFuture<Message>> localResult
                    = localHandlerRegistry.handle(new DeserializingMessage(message, messageType, serializer));
            if (localResult.isEmpty()) {
                SerializedMessage serializedMessage = dispatchInterceptor.modifySerializedMessage(
                        message.serialize(serializer), message, messageType);
                if (serializedMessage == null) {
                    continue;
                }
                serializedMessages.add(serializedMessage);
            } else if (localResult.get().isCompletedExceptionally()) {
                try {
                    localResult.get().getNow(null);
                } catch (CompletionException e) {
                    log.error("Handler failed to handle a {}",
                              message.getPayloadClass().getSimpleName(), e.getCause());
                }
            }
        }
        try {
            return gatewayClient.send(guarantee, serializedMessages.toArray(new SerializedMessage[0]));
        } catch (Exception e) {
            throw new GatewayException(format("Failed to send and forget %s messages", messages.length), e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CompletableFuture<Message>> sendForMessages(Message... messages) {
        List<Object> results = new ArrayList<>(messages.length);
        for (Message message : messages) {
            message = dispatchInterceptor.interceptDispatch(message, messageType);
            if (message == null) {
                results.add(emptyReturnMessage());
                continue;
            }
            Optional<CompletableFuture<Message>> localResult
                    = localHandlerRegistry.handle(new DeserializingMessage(message, messageType, serializer));
            if (localResult.isPresent()) {
                CompletableFuture<Message> c = localResult.get();
                if (messageType == MessageType.WEBREQUEST) {
                    c = c.thenApply(WebResponse::new);
                }
                String messageId = message.getMessageId();
                callbacks.put(messageId, c);
                results.add(c.whenComplete((m, e) -> callbacks.remove(messageId)));
            } else {
                SerializedMessage serializedMessage = dispatchInterceptor.modifySerializedMessage(
                        message.serialize(serializer), message, messageType);
                if (serializedMessage == null) {
                    results.add(emptyReturnMessage());
                    continue;
                }
                results.add(serializedMessage);
            }
        }
        List<SerializedMessage> serializedMessages = results.stream().filter(r -> r instanceof SerializedMessage)
                .map(m -> (SerializedMessage) m).collect(Collectors.toList());
        List<CompletableFuture<Message>> externalResults = serializedMessages.isEmpty()
                ? Collections.emptyList() : requestHandler.sendRequests(
                        serializedMessages, m -> gatewayClient.send(SENT, m.toArray(SerializedMessage[]::new))).stream()
                .map(r -> r.thenCompose(m -> {
                    Object result;
                    try {
                        result = serializer.deserialize(m.getData());
                    } catch (Exception e) {
                        log.error("Failed to deserialize result with id {}", m.getMessageId(), e);
                        return CompletableFuture.failedFuture(e);
                    }
                    if (result instanceof Throwable) {
                        return CompletableFuture.failedFuture((Throwable) result);
                    } else {
                        Message message = new Message(result, m.getMetadata());
                        if (messageType == MessageType.WEBREQUEST) {
                            message = new WebResponse(message);
                        }
                        return CompletableFuture.completedFuture(message);
                    }
                })).toList();

        return results.stream().map(r -> {
            if (r instanceof CompletableFuture<?>) {
                return (CompletableFuture<Message>) r;
            } else {
                SerializedMessage m = (SerializedMessage) r;
                CompletableFuture<Message> future = externalResults.get(serializedMessages.indexOf(m));
                callbacks.put(m.getMessageId(), future);
                return future.whenComplete((v, e) -> callbacks.remove(m.getMessageId()));
            }
        }).collect(Collectors.toList());
    }

    protected CompletableFuture<Message> emptyReturnMessage() {
        CompletableFuture<Message> c = CompletableFuture.completedFuture(Message.asMessage(null));
        if (messageType == MessageType.WEBREQUEST) {
            c = c.thenApply(WebResponse::new);
        }
        return c;
    }

    @Override
    public void close() {
        waitForResults(Duration.ofSeconds(2), callbacks.values());
    }
}
