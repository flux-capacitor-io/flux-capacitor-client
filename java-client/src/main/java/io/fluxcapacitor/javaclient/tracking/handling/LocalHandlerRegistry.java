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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultInvokerFactory;

@AllArgsConstructor
@Slf4j
public class LocalHandlerRegistry implements HandlerRegistry {
    private static final HandlerConfiguration<DeserializingMessage> localHandlerConfiguration =
            HandlerConfiguration.<DeserializingMessage>builder().handlerFilter(ClientUtils::isLocalHandlerMethod)
                    .invokerFactory(defaultInvokerFactory).build();

    private final MessageType messageType;
    private final HandlerFactory handlerFactory;
    private final Serializer serializer;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Override
    public Registration registerHandler(Object target) {
        return registerHandler(target, localHandlerConfiguration);
    }

    @Override
    public Registration registerHandler(Object target,
                                        HandlerConfiguration<DeserializingMessage> handlerConfiguration) {
        Optional<Handler<DeserializingMessage>> handler =
                handlerFactory.createHandler(target, "local-" + messageType, handlerConfiguration);
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    @Override
    public Optional<CompletableFuture<Message>> handle(Object payload, SerializedMessage serializedMessage) {
        if (!localHandlers.isEmpty()) {
            return new DeserializingMessage(serializedMessage, type -> serializer.convert(payload, type),
                                            messageType).apply(m -> {
                boolean handled = false;
                CompletableFuture<Message> future = new CompletableFuture<>();
                for (Handler<DeserializingMessage> handler : localHandlers) {
                    if (handler.canHandle(m)) {
                        boolean passive = handler.isPassive(m);
                        try {
                            Object result = handler.invoke(m);
                            if (!passive && !future.isDone()) {
                                if (result instanceof CompletableFuture<?>) {
                                    future = ((CompletableFuture<?>) result).thenApply(Message::new);
                                } else {
                                    future.complete(new Message(result));
                                }
                            }
                        } catch (Exception e) {
                            if (passive) {
                                log.error("Passive local handler {} failed to handle a {}", handler,
                                          m.getPayloadClass(), e);
                            } else {
                                future.completeExceptionally(e);
                            }
                        } finally {
                            if (!passive) {
                                handled = true;
                            }
                        }
                    }
                }
                return handled ? Optional.of(future) : Optional.empty();
            });
        }
        return Optional.empty();
    }
}
