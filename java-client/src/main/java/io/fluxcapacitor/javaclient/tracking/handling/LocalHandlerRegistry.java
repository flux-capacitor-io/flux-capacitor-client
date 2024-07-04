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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;
import static java.util.Collections.emptyList;

@RequiredArgsConstructor
@Slf4j
public class LocalHandlerRegistry implements HandlerRegistry {
    private final MessageType messageType;
    @Getter
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Setter
    @Getter
    @NonNull
    private HandlerFilter selfHandlerFilter = (t, m) -> !ClientUtils.isSelfTracking(t, m);

    private final Function<Class<?>, Optional<Handler<DeserializingMessage>>> selfHandlers
            = memoize(payloadType -> getHandlerFactory().createHandler(payloadType, getSelfHandlerFilter(), List.of()));

    @SuppressWarnings("unchecked")
    @Override
    public Registration registerHandler(Object target, HandlerFilter handlerFilter) {
        if (target instanceof Handler<?>) {
            localHandlers.add((Handler<DeserializingMessage>) target);
            return () -> localHandlers.remove(target);
        }
        Optional<Handler<DeserializingMessage>> handler = handlerFactory.createHandler(
                target, handlerFilter, emptyList());
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<CompletableFuture<Object>> handle(DeserializingMessage message) {
        List<Handler<DeserializingMessage>> localHandlers = getLocalHandlers(message);
        if (localHandlers.isEmpty()) {
            return Optional.empty();
        }
        return message.apply(m -> {
            boolean handled = false;
            boolean logMessage = false;
            boolean request = m.getMessageType().isRequest();
            CompletableFuture<Object> future = new CompletableFuture<>();
            for (Handler<DeserializingMessage> handler : localHandlers) {
                var optionalInvoker = handler.getInvoker(m);
                if (optionalInvoker.isPresent()) {
                    var invoker = optionalInvoker.get();
                    boolean passive = invoker.isPassive();
                    if (!handled || !request || passive) {
                        try {
                            Object result = Invocation.performInvocation(invoker::invoke);
                            if (!passive && !future.isDone()) {
                                if (result instanceof CompletableFuture<?>) {
                                    future = ((CompletableFuture<Object>) result);
                                } else {
                                    future.complete(result);
                                }
                            }
                        } catch (Throwable e) {
                            if (passive) {
                                log.error("Passive handler {} failed to handle a {}", invoker, m.getPayloadClass(),
                                          e);
                            } else {
                                future.completeExceptionally(e);
                            }
                        } finally {
                            if (!passive) {
                                handled = true;
                            }
                            logMessage = logMessage || logMessage(invoker);
                        }
                    }
                }
            }
            try {
                return handled ? Optional.of(future) : Optional.empty();
            } finally {
                if (logMessage) {
                    FluxCapacitor.getOptionally().ifPresent(fc -> fc.client().getGatewayClient(m.getMessageType())
                            .append(Guarantee.NONE, message.getSerializedObject()));
                }
            }
        });
    }

    protected List<Handler<DeserializingMessage>> getLocalHandlers(DeserializingMessage message) {
        if (!message.getMessageType().isRequest()) {
            return localHandlers;
        }
        return message.apply(m -> selfHandlers.apply(m.getPayloadClass())
                .map(h -> Stream.concat(localHandlers.stream(), Stream.of(h)).toList()).orElse(localHandlers));
    }

    protected boolean logMessage(HandlerInvoker invoker) {
        return getLocalHandlerAnnotation(invoker.getTargetClass(), invoker.getMethod())
                .map(LocalHandler::logMessage).orElse(false);
    }
}
