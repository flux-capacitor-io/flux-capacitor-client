package io.fluxcapacitor.javaclient.tracking.handler;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.reflect.Parameter;
import java.util.function.Function;

public class PayloadParameterResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            return DeserializingMessage::getPayload;
        }
        return null;
    }

    @Override
    public Function<DeserializingMessage, ? extends Class<?>> resolveClass(Parameter p) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            return DeserializingMessage::getPayloadClass;
        }
        return null;
    }
}
