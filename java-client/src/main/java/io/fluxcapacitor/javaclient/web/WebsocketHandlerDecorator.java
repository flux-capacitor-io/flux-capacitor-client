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
    public Handler<DeserializingMessage> wrap(Handler<DeserializingMessage> handler, String consumer) {
        var methods = ReflectionUtils.getAllMethods(handler.getTarget().getClass()).stream()
                .flatMap(m -> WebUtils.getWebParameters(m).stream()).filter(p -> p.getMethod().isWebsocket()).toList();
        if (!methods.isEmpty()) {
            methods.stream().filter(p -> p.getMethod() == HttpRequestMethod.WS_HANDSHAKE)
                    .map(WebParameters::getPath).distinct().forEach(websocketPaths::add);
            var pathsRequiringHandshake = methods.stream().map(WebParameters::getPath).distinct()
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
            this.handshakeInvoker = new HandshakeInvoker(delegate.getTarget());
        }

        @Override
        public Optional<HandlerInvoker> findInvoker(DeserializingMessage message) {
            return delegate.findInvoker(message).or(
                    () -> matches(message) ? Optional.of(handshakeInvoker) : Optional.empty());
        }

        protected boolean matches(DeserializingMessage message) {
            return message.getMessageType() == MessageType.WEBREQUEST
                   && WebRequest.getMethod(message.getMetadata()) == HttpRequestMethod.WS_HANDSHAKE
                   && paths.contains(WebRequest.getUrl(message.getMetadata()));
        }

        @Override
        public Object getTarget() {
            return delegate.getTarget();
        }

    }

    @RequiredArgsConstructor
    protected static class HandshakeInvoker implements HandlerInvoker {
        private final Object target;
        private final Executable method = getInvokeMethod();

        @SneakyThrows
        private static Method getInvokeMethod() {
            return HandshakeInvoker.class.getMethod("invoke");
        }

        @Override
        public Object getTarget() {
            return target;
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
