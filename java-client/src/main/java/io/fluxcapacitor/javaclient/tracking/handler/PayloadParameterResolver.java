package io.fluxcapacitor.javaclient.tracking.handler;

import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.Message;

import java.lang.reflect.Parameter;
import java.util.function.Function;

public class PayloadParameterResolver implements ParameterResolver<Message> {
    @Override
    public Function<Message, Object> resolve(Parameter p) {
        if (p.getDeclaringExecutable().getParameters()[0] == p) {
            return Message::getPayload;
        }
        return null;
    }
}
