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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.Invocation;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.asInstance;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;

@AllArgsConstructor
@Slf4j
public class LocalHandlerRegistry implements HandlerRegistry {
    private final MessageType messageType;
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unchecked")
    @Override
    public Registration registerHandler(Object target, HandlerFilter handlerFilter) {
        if (target instanceof Handler<?>) {
            localHandlers.add((Handler<DeserializingMessage>) target);
            return () -> localHandlers.remove(target);
        }
        Optional<Handler<DeserializingMessage>> handler =
                handlerFactory.createHandler(asInstance(target), "local-" + messageType, handlerFilter);
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    @Override
    public Optional<CompletableFuture<Message>> handle(DeserializingMessage message) {
        if (!localHandlers.isEmpty()) {
            return message.apply(m -> {
                boolean handled = false;
                boolean logMessage = false;
                CompletableFuture<Message> future = new CompletableFuture<>();
                for (Handler<DeserializingMessage> handler : localHandlers) {
                    Optional<HandlerInvoker> optionalInvoker = handler.findInvoker(m);
                    if (optionalInvoker.isPresent()) {
                        HandlerInvoker invoker = optionalInvoker.get();
                        boolean passive = invoker.isPassive();
                        try {
                            Object result = Invocation.performInvocation(invoker::invoke);
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
                            logMessage = logMessage || getLocalHandlerAnnotation(
                                    handler.getTarget().getClass(), invoker.getMethod())
                                    .map(LocalHandler::logMessage).orElse(false);
                        }
                    }
                }
                try {
                    return handled ? Optional.of(future) : Optional.empty();
                } finally {
                    if (logMessage) {
                        FluxCapacitor.getOptionally().ifPresent(fc -> fc.client().getGatewayClient(m.getMessageType())
                                .send(Guarantee.NONE, message.getSerializedObject()));
                    }
                }
            });
        }
        return Optional.empty();
    }
}
