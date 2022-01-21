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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
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
            SerializedMessage serializedMessage
                    = dispatchInterceptor.modifySerializedMessage(message.serialize(serializer), message, messageType);
            Optional<CompletableFuture<Message>> localResult
                    = localHandlerRegistry.handle(message.getPayload(), serializedMessage);
            if (localResult.isEmpty()) {
                serializedMessages.add(serializedMessage);
            } else if (localResult.get().isCompletedExceptionally()) {
                try {
                    localResult.get().getNow(null);
                } catch (CompletionException e) {
                    throw e.getCause();
                }
            }
        }
        try {
            return gatewayClient.send(guarantee, serializedMessages.toArray(new SerializedMessage[0]))
                    .asCompletableFuture();
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
            SerializedMessage serializedMessage
                    = dispatchInterceptor.modifySerializedMessage(message.serialize(serializer), message, messageType);
            Optional<CompletableFuture<Message>> localResult
                    = localHandlerRegistry.handle(message.getPayload(), serializedMessage);
            if (localResult.isPresent()) {
                CompletableFuture<Message> c = localResult.get();
                callbacks.put(serializedMessage.getMessageId(), c);
                results.add(c.whenComplete((m, e) -> callbacks.remove(serializedMessage.getMessageId())));
            } else {
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
                        return CompletableFuture.completedFuture(new Message(result, m.getMetadata()));
                    }
                })).collect(Collectors.toList());

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

    @Override
    public void close() {
        waitForResults(Duration.ofSeconds(2), callbacks.values());
    }
}
