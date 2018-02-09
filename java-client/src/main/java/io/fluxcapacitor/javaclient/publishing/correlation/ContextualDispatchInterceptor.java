package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;

import java.util.Optional;
import java.util.function.Function;

public abstract class ContextualDispatchInterceptor implements HandlerInterceptor, DispatchInterceptor {

    private final ThreadLocal<DeserializingMessage> currentMessage = new ThreadLocal<>();

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function) {
        return message -> {
            currentMessage.set(message);
            try {
                return function.apply(message);
            } finally {
                currentMessage.remove();
            }
        };
    }

    protected Optional<DeserializingMessage> getCurrentMessage() {
        return Optional.ofNullable(currentMessage.get());
    }

}
