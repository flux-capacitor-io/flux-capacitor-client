package io.fluxcapacitor.javaclient.tracking.handler;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.lang.reflect.Parameter;
import java.util.function.Function;

public class MetadataParameterResolver implements ParameterResolver<DeserializingMessage> {
    @Override
    public Function<DeserializingMessage, Object> resolve(Parameter p) {
        if (p.getType().equals(Metadata.class)) {
            return DeserializingMessage::getMetadata;
        }
        return null;
    }
}
