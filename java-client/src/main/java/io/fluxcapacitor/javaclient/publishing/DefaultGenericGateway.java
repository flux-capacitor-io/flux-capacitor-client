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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static io.fluxcapacitor.common.Guarantee.SENT;
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

    @Override
    @SneakyThrows
    public CompletableFuture<Void> sendAndForget(Message message, Guarantee guarantee) {
        message = dispatchInterceptor.interceptDispatch(message, messageType);
        SerializedMessage serializedMessage
                = dispatchInterceptor.modifySerializedMessage(message.serialize(serializer), message, messageType);
        Optional<CompletableFuture<Message>> localResult
                = localHandlerRegistry.handle(message.getPayload(), serializedMessage);
        if (localResult.isEmpty()) {
            try {
                return gatewayClient.send(guarantee, serializedMessage).asCompletableFuture();
            } catch (Exception e) {
                throw new GatewayException(format("Failed to send and forget %s", message.getPayload().toString()), e);
            }
        } else if (localResult.get().isCompletedExceptionally()) {
            try {
                localResult.get().getNow(null);
            } catch (CompletionException e) {
                throw e.getCause();
            }
        }
        return CompletableFuture.allOf();
    }

    @Override
    public CompletableFuture<Message> sendForMessage(Message message) {
        message = dispatchInterceptor.interceptDispatch(message, messageType);
        SerializedMessage serializedMessage
                = dispatchInterceptor.modifySerializedMessage(message.serialize(serializer), message, messageType);
        Optional<CompletableFuture<Message>> localResult
                = localHandlerRegistry.handle(message.getPayload(), serializedMessage);
        if (localResult.isPresent()) {
            return localResult.get();
        } else {
            try {
                return requestHandler.sendRequest(serializedMessage, messages -> gatewayClient.send(SENT, messages))
                        .thenCompose(m -> {
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
                        });
            } catch (Exception e) {
                throw new GatewayException(format("Failed to send %s", message.getPayload().toString()), e);
            }
        }
    }
}
