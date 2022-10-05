package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;
import java.util.Optional;

public class DefaultEntityMatcher implements EntityMatcher {

    private final ApplyMatcher applyMatcher;

    public DefaultEntityMatcher(List<ParameterResolver<? super DeserializingMessage>> parameterResolvers) {
        applyMatcher = new ApplyMatcher(parameterResolvers);
    }

    @Override
    public Optional<HandlerInvoker> applyInvoker(Entity<?> entity, DeserializingMessage message) {
        return applyMatcher.findInvoker(entity, message);
    }
}
