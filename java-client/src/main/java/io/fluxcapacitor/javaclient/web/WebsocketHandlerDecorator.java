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
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerDecorator;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiFunction;

public class WebsocketHandlerDecorator implements HandlerDecorator {
    private final Set<String> websocketPaths = new CopyOnWriteArraySet<>();

    @Override
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler) {
        var methods = ReflectionUtils.getAllMethods(handler.getTargetClass()).stream()
                .flatMap(m -> WebUtils.getWebPatterns(m).stream()).filter(p -> p.getMethod().isWebsocket()).toList();
        if (!methods.isEmpty()) {
            methods.stream().filter(p -> p.getMethod() == HttpRequestMethod.WS_HANDSHAKE)
                    .map(WebPattern::getPath).distinct().forEach(websocketPaths::add);
            var pathsRequiringHandshake = methods.stream().map(WebPattern::getPath).distinct()
                    .filter(websocketPaths::add).toList();
            if (!pathsRequiringHandshake.isEmpty()) {
                return new WebsocketHandshakeHandler(handler, pathsRequiringHandshake);
            }
        }
        return handler;
    }

    protected static class WebsocketHandshakeHandler implements Handler<DeserializingMessage> {
        private final Handler<DeserializingMessage> delegate;
        private final Collection<String> paths;
        private final HandlerInvoker handshakeInvoker;

        public WebsocketHandshakeHandler(Handler<DeserializingMessage> delegate, Collection<String> paths) {
            this.delegate = delegate;
            this.paths = paths;
            this.handshakeInvoker = new HandshakeInvoker(delegate.getTargetClass());
        }

        @Override
        public Optional<HandlerInvoker> getInvoker(DeserializingMessage message) {
            return delegate.getInvoker(message).or(
                    () -> matches(message) ? Optional.of(handshakeInvoker) : Optional.empty());
        }

        protected boolean matches(DeserializingMessage message) {
            return message.getMessageType() == MessageType.WEBREQUEST
                   && WebRequest.getMethod(message.getMetadata()) == HttpRequestMethod.WS_HANDSHAKE
                   && paths.contains(WebRequest.getUrl(message.getMetadata()));
        }

        @Override
        public Class<?> getTargetClass() {
            return delegate.getTargetClass();
        }
    }

    @RequiredArgsConstructor
    protected static class HandshakeInvoker implements HandlerInvoker {
        private final Class<?> targetClass;
        private final Executable method = getInvokeMethod();

        @SneakyThrows
        private static Method getInvokeMethod() {
            return HandshakeInvoker.class.getMethod("invoke");
        }

        @Override
        public Class<?> getTargetClass() {
            return targetClass;
        }

        @Override
        public Executable getMethod() {
            return method;
        }

        @Override
        public <A extends Annotation> A getMethodAnnotation() {
            return null;
        }

        @Override
        public boolean expectResult() {
            return false;
        }

        @Override
        public boolean isPassive() {
            return false;
        }

        @Override
        public Object invoke(BiFunction<Object, Object, Object> combiner) {
            return null;
        }
    }
}
