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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.HandlerMatcher;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.MutableHandler;
import io.fluxcapacitor.javaclient.tracking.handling.RepositoryProvider;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getTypeAnnotation;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_CLOSE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_MESSAGE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_OPEN;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_PONG;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;

/**
 * A specialized {@link Handler} that manages lifecycle events and message dispatching for WebSocket endpoints annotated
 * with {@link SocketEndpoint}.
 * <p>
 * This handler supports:
 * <ul>
 *     <li>Instantiating and caching per-session WebSocket handler instances</li>
 *     <li>Delegating to the correct handler method (e.g., {@code @HandleSocketOpen}, {@code @HandleSocketMessage},
 *     {@code @HandleSocketPong}, {@code @HandleSocketClose})</li>
 *     <li>Managing handler lifecycle via {@link SocketEndpointWrapper}, including automatic cleanup and ping-based
 *     connection health checks</li>
 * </ul>
 * <p>
 * When a {@link DeserializingMessage} is received, the handler checks whether it represents a WebSocket request.
 * If so, it resolves or initializes a {@link SocketEndpointWrapper} associated with the given session ID and delegates
 * message handling to the appropriate method. For non-WebSocket messages, the handler behaves like a typical
 * {@link Handler} by invoking any matching method on the target class or on cached wrappers.
 * <p>
 * This class is primarily intended for internal use by the Flux dispatcher infrastructure.
 *
 * @see SocketEndpoint
 * @see SocketSession
 * @see Handler
 * @see HandlerInvoker
 */
@Getter
@AllArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@Slf4j
public class SocketEndpointHandler implements Handler<DeserializingMessage> {
    Class<?> targetClass;
    HandlerMatcher<Object, DeserializingMessage> targetMatcher, wrapperMatcher;
    Map<Object, SocketEndpointWrapper> repository;

    @Getter(lazy = true)
    SocketEndpoint socketEndpoint = getTypeAnnotation(targetClass, SocketEndpoint.class);

    public SocketEndpointHandler(Class<?> targetClass,
                                 HandlerMatcher<Object, DeserializingMessage> targetMatcher,
                                 HandlerMatcher<Object, DeserializingMessage> wrapperMatcher,
                                 RepositoryProvider repositoryProvider) {
        this.targetClass = targetClass;
        this.targetMatcher = targetMatcher;
        this.wrapperMatcher = wrapperMatcher;
        this.repository = repositoryProvider.getRepository(SocketEndpointWrapper.class);
    }

    @Override
    public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
        if (message.getMessageType() == MessageType.WEBREQUEST) {
            switch (WebRequest.getMethod(message.getMetadata())) {
                case WS_OPEN, WS_MESSAGE, WS_PONG, WS_CLOSE -> {
                    String sessionId = WebRequest.getSocketSessionId(message.getMetadata());
                    if (sessionId == null) {
                        log.warn("No sessionId found in message {}", message.getMessageId());
                        return Optional.empty();
                    }
                    return ofNullable(repository.computeIfAbsent(
                            sessionId, sId -> !targetMatcher.canHandle(message) ? null : new SocketEndpointWrapper(
                                    getSocketEndpoint(), new MutableHandler<>(targetClass, targetMatcher, false, null),
                                    () -> repository.remove(sId))))
                            .flatMap(wrapper -> {
                                Optional<HandlerInvoker> wrapperInvoker = wrapperMatcher.getInvoker(wrapper, message);
                                return wrapperInvoker.map(w -> {
                                    var targetInvoker = wrapper.targetHandler.getInvoker(message);
                                    if (targetInvoker.isEmpty()) {
                                        return w;
                                    }
                                    var t = targetInvoker.get();
                                    return new HandlerInvoker.DelegatingHandlerInvoker(t) {
                                        @Override
                                        public Object invoke(BiFunction<Object, Object, Object> resultCombiner) {
                                            return w.invoke(resultCombiner);
                                        }
                                    };
                                });
                            });
                }
            }
        }
        return targetMatcher.canHandle(message)
                ? HandlerInvoker.join(concat(
                targetMatcher.getInvoker(null, message).stream(), repository.values().stream().flatMap(
                        i -> targetMatcher.getInvoker(i.unwrap(), message).stream())).toList())
                : Optional.empty();
    }

    @Override
    public String toString() {
        return "SocketEndpointHandler[%s]".formatted(targetClass);
    }

    /**
     * A stateful wrapper around a WebSocket endpoint instance, managing per-session behavior and message handling.
     * <p>
     * This class is created per WebSocket session and holds:
     * <ul>
     *     <li>A lazily instantiated target handler</li>
     *     <li>Connection health tracking (ping/pong logic)</li>
     *     <li>Automatic session cleanup via {@link Registration} hooks</li>
     *     <li>Delegation to matching methods for open, message, pong, and close events</li>
     * </ul>
     * <p>
     * When the first {@code @HandleSocketOpen} or {@code @HandleSocketMessage} message arrives for a session,
     * the target handler is initialized and stored. Subsequent WebSocket lifecycle messages (e.g. {@code @HandleSocketPong},
     * {@code @HandleSocketClose}) are routed to the correct methods on the same instance.
     * <p>
     * Ping scheduling and timeout behavior is based on the {@link SocketEndpoint#aliveCheck()} configuration.
     *
     * @see HandleSocketOpen
     * @see HandleSocketMessage
     * @see HandleSocketPong
     * @see HandleSocketClose
     * @see SocketEndpoint
     */
    @Path("*")
    @RequiredArgsConstructor
    public static class SocketEndpointWrapper {
        private final SocketEndpoint annotation;
        private final MutableHandler<DeserializingMessage> targetHandler;
        private final Registration closeCallback;
        private final AtomicBoolean closed = new AtomicBoolean();

        private volatile SocketSession session;
        private volatile Registration pingDeadline;

        @Getter(lazy = true)
        private final Duration pingDelay = annotation.aliveCheck().value() ?
                Duration.of(annotation.aliveCheck().pingDelay(),
                            annotation.aliveCheck().timeUnit().toChronoUnit()) : null;
        @Getter(lazy = true)
        private final Duration pingTimeout = annotation.aliveCheck().value() ?
                Duration.of(annotation.aliveCheck().pingTimeout(),
                            annotation.aliveCheck().timeUnit().toChronoUnit()) : null;

        @HandleSocketOpen
        @HandleSocketMessage
        protected Object onOpenOrMessage(SocketSession session, DeserializingMessage message) {
            if (this.session == null) {
                this.session = session;
                closed.set(false);
                targetHandler.onDelete(() -> abort(1000));
                FluxCapacitor.getOptionally().ifPresent(fc -> fc.beforeShutdown(() -> abort(1001)));
                trySchedulePing();
            }
            try {
                Optional<HandlerInvoker> invoker = targetHandler.getInvoker(message);
                if (invoker.isEmpty() && targetHandler.isEmpty()) {
                    try {
                        targetHandler.instantiateTarget();
                    } catch (Throwable e) {
                        log.error(
                                "SocketEndpoint of type {} is missing a factory handler method or default constructor.",
                                targetHandler.getTargetClass());
                        throw e;
                    }
                    invoker = targetHandler.instantiateTarget().getInvoker(message);
                }
                return invoker.map(HandlerInvoker::invoke).orElse(null);
            } catch (Throwable t) {
                abort(1006);
                throw t;
            }
        }

        @HandleSocketPong
        protected void onPong(DeserializingMessage message) {
            try {
                targetHandler.getInvoker(message).ifPresent(HandlerInvoker::invoke);
            } finally {
                ofNullable(pingDeadline).ifPresent(Registration::cancel);
                trySchedulePing();
            }
        }

        protected void trySchedulePing() {
            ofNullable(getPingDelay()).ifPresent(delay -> {
                if (isOpen()) {
                    var taskScheduler = FluxCapacitor.get().taskScheduler();
                    pingDeadline = taskScheduler.schedule(delay, () -> sendPing(getPingTimeout(), taskScheduler));
                }
            });
        }

        protected void sendPing(Duration pingTimeout, TaskScheduler taskScheduler) {
            if (isOpen()) {
                pingDeadline = taskScheduler.schedule(pingTimeout, () -> {
                    log.warn("Failed to get a ping response in time for session {}. Closing connection.",
                             session.sessionId());
                    abort(1002);
                });
                try {
                    session.sendPing(getClass().getSimpleName());
                } catch (Exception e) {
                    log.warn("Failed to send ping message", e);
                }
            }
        }

        @HandleSocketClose
        protected void onClose(DeserializingMessage message) {
            ofNullable(pingDeadline).ifPresent(Registration::cancel);
            try {
                targetHandler.getInvoker(message).ifPresent(HandlerInvoker::invoke);
            } finally {
                if (closed.compareAndSet(false, true)) {
                    closeCallback.cancel();
                }
            }
        }

        protected void abort(int closeCode) {
            if (isOpen()) {
                session.close(closeCode);
            }
        }

        protected boolean isOpen() {
            return !closed.get();
        }

        protected Object unwrap() {
            return targetHandler.getTarget();
        }
    }
}
