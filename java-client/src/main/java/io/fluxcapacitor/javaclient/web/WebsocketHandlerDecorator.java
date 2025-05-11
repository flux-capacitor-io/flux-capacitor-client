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
import io.fluxcapacitor.javaclient.publishing.ResultGateway;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerDecorator;
import lombok.RequiredArgsConstructor;

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
import static io.fluxcapacitor.javaclient.web.HttpRequestMethod.isWebsocket;
import static io.fluxcapacitor.javaclient.web.WebRequest.requireSocketSessionId;
import static java.util.Optional.empty;

@RequiredArgsConstructor
public class WebsocketHandlerDecorator implements HandlerDecorator, ParameterResolver<HasMessage> {
    private final ResultGateway webResponseGateway;

    private final Set<String> websocketPaths = new CopyOnWriteArraySet<>();
    private final Map<String, SocketSession> openSessions = new ConcurrentHashMap<>();

    @Override
    public Function<HasMessage, Object> resolve(Parameter p, Annotation methodAnnotation) {
        return m -> openSessions.computeIfAbsent(requireSocketSessionId(m.getMetadata()), sId -> {
            String target = m instanceof DeserializingMessage dm ? dm.getSerializedObject().getSource() : null;
            return new DefaultSocketSession(sId, target, webResponseGateway, () -> onClose(sId));
        });
    }

    @Override
    public boolean matches(Parameter parameter, Annotation methodAnnotation, HasMessage value) {
        return SocketSession.class.isAssignableFrom(parameter.getType())
               && ReflectionUtils.isOrHas(methodAnnotation, HandleWeb.class);
    }

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        var socketPatterns = ReflectionUtils.getAllMethods(handler.getTargetClass()).stream()
                .flatMap(m -> WebUtils.getWebPatterns(m).stream())
                .filter(p -> isWebsocket(p.getMethod())).toList();
        if (!socketPatterns.isEmpty()) {
            handler = enableHandshake(handler, socketPatterns);
            handler = removeSessionOnClose(handler);
        }
        return handler;
    }

    protected void onClose(String sessionId) {
        openSessions.remove(sessionId);
    }

    protected Handler<DeserializingMessage> removeSessionOnClose(Handler<DeserializingMessage> handler) {
        return new DelegatingHandler<DeserializingMessage>(handler) {
            @Override
            public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
                if (!matches(message)) {
                    return delegate.getInvoker(message);
                }
                HandlerInvoker remover = HandlerInvoker.run(
                        () -> onClose(requireSocketSessionId(message.getMetadata())));
                return delegate.getInvoker(message).map(i -> i.andFinally(remover))
                        .or(() -> Optional.of(remover));
            }

            boolean matches(DeserializingMessage message) {
                return message.getMessageType() == WEBREQUEST
                       && WS_CLOSE.equals(WebRequest.getMethod(message.getMetadata()));
            }
        };
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

}
