package io.fluxcapacitor.javaclient.tracking.handler;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.Message;

import java.lang.reflect.Parameter;
import java.util.function.Function;

public class MetadataParameterResolver implements ParameterResolver<Message> {
    @Override
    public Function<Message, Object> resolve(Parameter p) {
        if (p.getType().equals(Metadata.class)) {
            return Message::getMetadata;
        }
        return null;
    }
}
