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
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.WEBREQUEST;
import static io.fluxcapacitor.javaclient.web.DefaultWebRequestContext.getWebRequestContext;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_CLOSE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_HANDSHAKE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.WS_MESSAGE;
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.isWebsocket;
import static io.fluxcapacitor.javaclient.web.WebRequest.requireSocketSessionId;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

@RequiredArgsConstructor
@Slf4j
public class WebsocketHandlerDecorator implements HandlerDecorator, ParameterResolver<HasMessage> {
    private final ResultGateway webResponseGateway;
    private final Serializer serializer;

    private final Set<String> websocketPaths = new CopyOnWriteArraySet<>();
    private final Map<String, DefaultSocketSession> openSessions = new ConcurrentHashMap<>();
    private final Set<Handler<DeserializingMessage>> websocketHandlers = new CopyOnWriteArraySet<>();

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> openSessions.computeIfAbsent(requireSocketSessionId(m.getMetadata()), sId -> {
            String target = m instanceof DeserializingMessage dm ? dm.getSerializedObject().getSource() : null;
            return new DefaultSocketSession(sId, target,
                                            WebRequest.getUrl(m.getMetadata()),
                                            WebRequest.getHeaders(m.getMetadata()),
                                            webResponseGateway, this::onAbort);
        });
    }

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

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        var socketPatterns = ReflectionUtils.getAllMethods(handler.getTargetClass()).stream()
                .flatMap(m -> WebUtils.getWebPatterns(m).stream())
                .filter(p -> isWebsocket(p.getMethod())).toList();
        if (!socketPatterns.isEmpty()) {
            handler = enableHandshake(handler, socketPatterns);
            handler = handleRequest(handler);
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
                        .flatMap(session -> session.tryRequest(message, delegate)
                                .or(() -> session.tryCompleteRequest(message)))
                        .or(() -> delegate.getInvoker(message));
            }

            boolean matches(DeserializingMessage message) {
                return message.getMessageType() == WEBREQUEST
                       && WS_MESSAGE.equals(WebRequest.getMethod(message.getMetadata()));
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
