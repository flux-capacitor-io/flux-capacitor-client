/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.lang.String.format;
import static java.util.concurrent.CompletableFuture.completedFuture;

@AllArgsConstructor
public class DefaultGenericGateway implements RequestGateway {
    private final MessageType messageType;
    private final GatewayClient gatewayClient;
    private final RequestHandler requestHandler;
    private final MessageSerializer serializer;
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void sendAndForget(Message message) {
        SerializedMessage serializedMessage = serializer.serialize(message);
        CompletableFuture<Message> localResult = tryHandleLocally(message.getPayload(), serializedMessage);
        if (localResult == null) {
            try {
                gatewayClient.send(serializedMessage);
            } catch (Exception e) {
                throw new GatewayException(format("Failed to send and forget %s", message.getPayload().toString()), e);
            }
        }
    }

    @Override
    public CompletableFuture<Message> sendForMessage(Message message) {
        SerializedMessage serializedMessage = serializer.serialize(message);
        CompletableFuture<Message> localResult = tryHandleLocally(message.getPayload(), serializedMessage);
        if (localResult == null) {
            try {
                return requestHandler.sendRequest(serializedMessage, gatewayClient::send);
            } catch (Exception e) {
                throw new GatewayException(format("Failed to send %s", message.getPayload().toString()), e);
            }
        } else {
            return localResult;
        }
    }

    @Override
    public Registration registerLocalHandler(Object target) {
        Optional<Handler<DeserializingMessage>> handler = handlerFactory.createHandler(target);
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    protected CompletableFuture<Message> tryHandleLocally(Object payload, SerializedMessage serializedMessage) {
        if (!localHandlers.isEmpty()) {
            DeserializingMessage current = DeserializingMessage.getCurrent();
            try {
                DeserializingMessage deserializingMessage =
                        new DeserializingMessage(new DeserializingObject<>(serializedMessage, () -> payload), messageType);
                DeserializingMessage.setCurrent(deserializingMessage);
                for (Handler<DeserializingMessage> handler : localHandlers) {
                    if (handler.canHandle(deserializingMessage)) {
                        try {
                            Object result = handler.invoke(deserializingMessage);
                            return result instanceof CompletableFuture<?> ?
                                    ((CompletableFuture<?>) result).thenApply(Message::new) :
                                    completedFuture(new Message(result));
                        } catch (Exception e) {
                            CompletableFuture<Message> result = new CompletableFuture<>();
                            result.completeExceptionally(e);
                            return result;
                        }
                    }
                }
            } finally {
                DeserializingMessage.setCurrent(current);
            }
        }
        return null;
    }

}
