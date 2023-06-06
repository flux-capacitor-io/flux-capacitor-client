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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.handling.HandlerFilter.ALWAYS_HANDLE;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.asInstance;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getHandleSelfAnnotation;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;
import static java.util.Collections.emptyList;

@AllArgsConstructor
@Slf4j
public class LocalHandlerRegistry implements HandlerRegistry {
    private final MessageType messageType;
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();
    private final Function<Class<?>, Optional<Handler<DeserializingMessage>>> selfHandlers
            = memoize(this::computeSelfHandler);

    @SuppressWarnings("unchecked")
    @Override
    public Registration registerHandler(Object target, HandlerFilter handlerFilter) {
        if (target instanceof Handler<?>) {
            localHandlers.add((Handler<DeserializingMessage>) target);
            return () -> localHandlers.remove(target);
        }
        Optional<Handler<DeserializingMessage>> handler = handlerFactory.createHandler(
                asInstance(target), "local-" + messageType, handlerFilter, emptyList());
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    @Override
    public Optional<CompletableFuture<Message>> handle(DeserializingMessage message) {
        if (!localHandlers.isEmpty() || handleSelf(message)) {
            List<HandlerInvoker> localHandlers = getLocalHandlers(message);
            return message.apply(m -> {
                boolean handled = false;
                boolean logMessage = false;
                CompletableFuture<Message> future = new CompletableFuture<>();
                for (HandlerInvoker invoker : localHandlers) {
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
                    } catch (Throwable e) {
                        if (passive) {
                            log.error("Passive handler {} failed to handle a {}", invoker, m.getPayloadClass(), e);
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

    protected boolean handleSelf(DeserializingMessage message) {
        return messageType.isRequest() && getHandleSelfAnnotation(message.getPayloadClass()).isPresent();
    }

    protected List<HandlerInvoker> getLocalHandlers(DeserializingMessage message) {
        if (!message.getMessageType().isRequest()) {
            List<HandlerInvoker> result = new ArrayList<>();
            for (Handler<DeserializingMessage> h : localHandlers) {
                h.findInvoker(message).ifPresent(result::add);
            }
            return result;
        }
        return message.apply(m -> selfHandlers.apply(m.getPayloadClass())
                .flatMap(h -> Optional.ofNullable(h.findInvoker(m).orElseGet(() -> {
                    log.warn("@HandleSelf method on payload class {} could not be invoked. "
                             + "Probably as result of an unresolved method parameter", m.getMessageType());
                    return null;
                })))
                .filter(selfHandler -> !selfHandler.<HandleSelf>getMethodAnnotation().disabled())
                .map(selfHandler -> {
                    List<HandlerInvoker> result = new ArrayList<>();
                    result.add(selfHandler);
                    if (selfHandler.<HandleSelf>getMethodAnnotation().continueHandling()) {
                        for (Handler<DeserializingMessage> h : localHandlers) {
                            h.findInvoker(m).ifPresent(result::add);
                        }
                    }
                    return result;
                }).orElseGet(() -> {
                    List<HandlerInvoker> result = new ArrayList<>();
                    for (Handler<DeserializingMessage> h : localHandlers) {
                        h.findInvoker(m).ifPresent(result::add);
                    }
                    return result;
                }));
    }

    protected boolean logMessage(HandlerInvoker invoker) {
        if (invoker.getMethodAnnotation() instanceof HandleSelf handleSelf) {
            return handleSelf.logMessage();
        }
        return getLocalHandlerAnnotation(
                invoker.getTarget().getClass(), invoker.getMethod())
                .map(LocalHandler::logMessage).orElse(false);
    }

    protected Optional<Handler<DeserializingMessage>> computeSelfHandler(Class<?> payloadType) {
        return handlerFactory.createHandler(
                () -> DeserializingMessage.getCurrent().getPayload(), payloadType, HandleSelf.class,
                "self-" + messageType, ALWAYS_HANDLE, emptyList());
    }
}
