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

import io.fluxcapacitor.common.TaskScheduler;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.Handler.DelegatingHandler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerDecorator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAllMethods;
import static io.fluxcapacitor.javaclient.web.DefaultWebRequestContext.getWebRequestContext;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_CLOSE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_HANDSHAKE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_MESSAGE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_OPEN;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.isWebsocket;
import static io.fluxcapacitor.javaclient.web.WebRequest.requireSocketSessionId;
import static java.util.Arrays.stream;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.concat;

/**
 * Decorator that adds WebSocket session support to handler classes and enables parameter injection for
 * {@link SocketSession}.
 * <p>
 * This decorator supports {@code @HandleWeb}-annotated methods that interact over WebSocket connections, and
 * transparently manages the lifecycle of {@link SocketSession} instances. It implements both {@link HandlerDecorator}
 * and {@link ParameterResolver} to provide the following capabilities:
 *
 * <ul>
 *   <li>
 *     Automatically upgrades incoming {@code WS_HANDSHAKE} requests to WebSocket sessions <b>if</b> the handler
 *     includes methods annotated with {@link io.fluxcapacitor.javaclient.web.HandleSocketOpen} or similar,
 *     but does not explicitly handle the handshake itself. This ensures that endpoints which declare websocket handlers
 *     still result in a successful connection upgrade even without a dedicated handshake method.
 *   </li>
 *   <li>
 *     Delegates {@code WS_MESSAGE} requests to the associated {@link SocketSession} for message dispatch and response.
 *   </li>
 *   <li>
 *     Performs cleanup on {@code WS_CLOSE} requests, closing the session and invoking any associated
 *     {@link io.fluxcapacitor.javaclient.web.HandleSocketClose} handlers.
 *   </li>
 *   <li>
 *     Resolves {@link SocketSession} method parameters in applicable WebSocket handler methods.
 *   </li>
 * </ul>
 * <p>
 * This decorator also tracks open WebSocket sessions and ensures that message dispatch is session-aware. It works
 * transparently with the Flux client’s message routing and tracking infrastructure.
 */
@RequiredArgsConstructor
@Slf4j
public class WebsocketHandlerDecorator implements HandlerDecorator, ParameterResolver<HasMessage> {
    private final ResultGateway webResponseGateway;
    private final Serializer serializer;
    private final TaskScheduler taskScheduler;

    private final Set<String> websocketPaths = new CopyOnWriteArraySet<>();
    private final Map<String, DefaultSocketSession> openSessions = new ConcurrentHashMap<>();
    private final Set<Handler<DeserializingMessage>> websocketHandlers = new CopyOnWriteArraySet<>();

    /**
     * Resolves a {@link SocketSession} parameter from the current {@link HasMessage} context.
     * <p>
     * The session is tracked per session ID and reused for subsequent requests over the same connection. If no session
     * exists for the current message's session ID, a new one is created.
     *
     * @param p                the method parameter being resolved
     * @param methodAnnotation the handler method's annotation (e.g. {@code @HandleWeb})
     * @return a function that resolves the {@link SocketSession} from the message
     */
    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return this::getOrCreateSocketSession;
    }

    protected DefaultSocketSession getOrCreateSocketSession(HasMessage m) {
        return openSessions.computeIfAbsent(requireSocketSessionId(m.getMetadata()), sId -> {
            String target = m instanceof DeserializingMessage dm ? dm.getSerializedObject().getSource() : null;
            return new DefaultSocketSession(sId, target,
                                            WebRequest.getUrl(m.getMetadata()),
                                            WebRequest.getHeaders(m.getMetadata()),
                                            webResponseGateway, taskScheduler, this::onAbort);
        });
    }

    /**
     * Determines whether this resolver supports injecting a {@link SocketSession} for the given method parameter.
     * <p>
     * The parameter must be assignable from {@link SocketSession}, and the method must be annotated with
     * {@link HandleWeb} (or a compatible meta-annotation).
     *
     * @param parameter        the parameter being resolved
     * @param methodAnnotation the annotation present on the handler method
     * @param value            the current message
     * @return {@code true} if the parameter should be resolved by this resolver
     */
    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value) {
        return SocketSession.class.isAssignableFrom(parameter.getType())
               && ReflectionUtils.isOrHas(methodAnnotation, HandleWeb.class);
    }

    protected void onAbort(DefaultSocketSession session, int code) {
        WebRequest abortRequest = WebRequest.builder()
                .headers(session.getHeaders()).url(session.getUrl())
                .method(WS_CLOSE).payload(String.valueOf(code)).build()
                .addMetadata("sessionId", session.sessionId());
        var message = new DeserializingMessage(abortRequest, WEBREQUEST, serializer);
        message.run(m -> {
            for (Handler<DeserializingMessage> handler : websocketHandlers) {
                try {
                    handler.getInvoker(message).ifPresent(HandlerInvoker::invoke);
                } catch (Throwable t) {
                    log.error("Failed to invoke @HandleClose method websocket handler: {}",
                              handler.getTargetClass(), t);
                }
            }
        });
    }

    /**
     * Wraps a websocket-compatible handler with websocket-specific functionality.
     * <p>
     * If the target handler supports websocket patterns (e.g., {@code WS_HANDSHAKE} or {@code WS_MESSAGE}), it is
     * wrapped with logic to support automatic handshake negotiation, websocket message dispatch, and session cleanup on
     * close.
     *
     * @param handler the original handler
     * @return the wrapped handler with websocket support if applicable
     */
    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        Class<?> type = handler.getTargetClass();
        var socketPatterns =
                concat(getAllMethods(type).stream(), stream(type.getDeclaredConstructors()))
                        .flatMap(m -> WebUtils.getWebPatterns(type, null, m).stream())
                        .filter(p -> isWebsocket(p.getMethod())).toList();
        if (!socketPatterns.isEmpty()) {
            handler = enableHandshake(handler, socketPatterns);
            handler = handleRequest(handler);
            handler = closeOnError(handler);
            handler = cleanUpOnClose(handler);
            websocketHandlers.add(handler);
        }
        return handler;
    }

    protected Handler<DeserializingMessage> enableHandshake(Handler<DeserializingMessage> handler,
                                                            List<WebPattern> socketPatterns) {
        socketPatterns.stream().filter(p -> WS_HANDSHAKE.equals(p.getMethod()))
                .map(WebPattern::getPath).distinct().forEach(websocketPaths::add);
        var pathsRequiringHandshake = socketPatterns.stream().map(WebPattern::getPath).distinct()
                .filter(websocketPaths::add).toList();
        if (!pathsRequiringHandshake.isEmpty()) {
            handler = new DelegatingHandler<DeserializingMessage>(handler) {
                @Override
                public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
                    return delegate.getInvoker(message)
                            .or(() -> matches(message) ? Optional.of(HandlerInvoker.noOp()) : empty());
                }

                boolean matches(DeserializingMessage message) {
                    return message.getMessageType() == WEBREQUEST
                           && WS_HANDSHAKE.equals(WebRequest.getMethod(message.getMetadata()))
                           && getWebRequestContext(message).matchesAny(pathsRequiringHandshake);
                }
            };
        }
        return handler;
    }

    protected Handler<DeserializingMessage> handleRequest(Handler<DeserializingMessage> handler) {
        return new DelegatingHandler<DeserializingMessage>(handler) {
            @Override
            public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
                if (!matches(message)) {
                    return delegate.getInvoker(message);
                }
                return ofNullable(openSessions.get(requireSocketSessionId(message.getMetadata())))
                        .flatMap(session -> session.tryHandleRequest(message, delegate)
                                .or(() -> session.tryCompleteRequest(message)))
                        .or(() -> delegate.getInvoker(message));
            }

            boolean matches(DeserializingMessage message) {
                return message.getMessageType() == WEBREQUEST
                       && WS_MESSAGE.equals(WebRequest.getMethod(message.getMetadata()));
            }
        };
    }

    protected Handler<DeserializingMessage> closeOnError(Handler<DeserializingMessage> handler) {
        return new DelegatingHandler<DeserializingMessage>(handler) {
            @Override
            public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
                if (!matches(message)) {
                    return delegate.getInvoker(message);
                }
                return delegate.getInvoker(message)
                        .map(i -> new HandlerInvoker.DelegatingHandlerInvoker(i) {
                            @Override
                            public Object invoke(BiFunction<Object, Object, Object> resultCombiner) {
                                try {
                                    return delegate.invoke(resultCombiner);
                                } catch (Throwable e) {
                                    try {
                                        getOrCreateSocketSession(message).close();
                                    } catch (Throwable t) {
                                        e.addSuppressed(t);
                                    }
                                    throw e;
                                }
                            }
                        });
            }

            boolean matches(DeserializingMessage message) {
                if (message.getMessageType() == WEBREQUEST) {
                    switch (WebRequest.getMethod(message.getMetadata())) {
                        case WS_OPEN, WS_MESSAGE -> {
                            return true;
                        }
                    }
                }
                return false;
            }
        };
    }

    protected Handler<DeserializingMessage> cleanUpOnClose(Handler<DeserializingMessage> handler) {
        return new DelegatingHandler<DeserializingMessage>(handler) {
            @Override
            public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
                if (!matches(message)) {
                    return delegate.getInvoker(message);
                }
                String sessionId = requireSocketSessionId(message.getMetadata());
                HandlerInvoker remover = HandlerInvoker.run(() -> ofNullable(openSessions.get(sessionId))
                        .ifPresent(session -> {
                            try {
                                session.onClose();
                            } finally {
                                openSessions.remove(session.sessionId(), session);
                            }
                        }));
                return delegate.getInvoker(message).map(i -> i.andFinally(remover))
                        .or(() -> Optional.of(remover));
            }

            boolean matches(DeserializingMessage message) {
                return message.getMessageType() == WEBREQUEST
                       && WS_CLOSE.equals(WebRequest.getMethod(message.getMetadata()));
            }
        };
    }

}
