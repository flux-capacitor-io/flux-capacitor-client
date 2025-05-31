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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;
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

/**
 * In-memory implementation of {@link HandlerRegistry} that manages and dispatches local message handlers — i.e.,
 * handlers that are invoked directly in the publishing thread without involving the Flux platform.
 * <p>
 * The {@code LocalHandlerRegistry} only registers and invokes handlers that meet the criteria for local handling. These
 * include:
 * <ul>
 *     <li>Methods explicitly annotated with {@link LocalHandler}</li>
 *     <li>Handlers defined inside a message payload class (e.g., query or command) <b>not</b> annotated with {@link TrackSelf}</li>
 * </ul>
 * <p>
 * This mechanism is useful for bypassing asynchronous tracking and Flux platform involvement when
 * immediate, in-process execution is preferred — such as for fast local queries, synchronous command handlers,
 * or test scenarios.
 *
 * <h2>Self-Handlers</h2>
 * <p>
 * If a message's payload type defines a handler method (e.g., {@code @HandleQuery}) and is not marked
 * with {@link TrackSelf}, then that handler is considered a "self-handler" and is treated as local. These handlers are
 * lazily constructed by the {@link HandlerFactory} and automatically included during message dispatch.
 *
 * <h2>Fallback to Flux Platform</h2>
 * If no local handlers are found for a given message, it will not be processed in the publishing thread. Instead:
 * <ul>
 *     <li>The message will be published to the Flux platform using the appropriate gateway</li>
 *     <li>It will be logged so that remote trackers or consumers can handle it asynchronously</li>
 * </ul>
 * <p>
 * This ensures consistent delivery semantics while giving applications control over what is handled locally.
 *
 * <h2>Thread Safety</h2>
 * Registered handlers are stored in a {@link java.util.concurrent.CopyOnWriteArrayList}, making the registry
 * safe for concurrent usage and dynamic handler registration.
 *
 * @see LocalHandler
 * @see TrackSelf
 * @see HandlerRegistry
 * @see HasLocalHandlers
 * @see HandlerFactory
 */
@RequiredArgsConstructor
@Slf4j
public class LocalHandlerRegistry implements HandlerRegistry {
    @Getter
    private final HandlerFactory handlerFactory;
    @Getter
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Setter
    @Getter
    @NonNull
    private HandlerFilter selfHandlerFilter = (t, m) -> !ClientUtils.isSelfTracking(t, m);

    private final Function<Class<?>, Optional<Handler<DeserializingMessage>>> selfHandlers
            = memoize(payloadType -> getHandlerFactory().createHandler(payloadType, getSelfHandlerFilter(), List.of()));

    @Override
    public boolean hasLocalHandlers() {
        return !localHandlers.isEmpty();
    }

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

    /**
     * Returns the full list of handlers that should be used to process the given message.
     * <p>
     * This may include a self-handler if the message is a request type.
     */
    protected List<Handler<DeserializingMessage>> getLocalHandlers(DeserializingMessage message) {
        if (!message.getMessageType().isRequest()) {
            return localHandlers;
        }
        return message.apply(m -> selfHandlers.apply(m.getPayloadClass())
                .map(h -> Stream.concat(localHandlers.stream(), Stream.of(h)).toList()).orElse(localHandlers));
    }

    /**
     * Determines whether a handler allows its message to be sent to the Flux platform.
     */
    protected boolean logMessage(HandlerInvoker invoker) {
        return getLocalHandlerAnnotation(invoker.getTargetClass(), invoker.getMethod())
                .map(LocalHandler::logMessage).orElse(false);
    }
}
