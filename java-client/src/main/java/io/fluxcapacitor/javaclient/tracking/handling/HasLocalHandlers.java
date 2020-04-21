package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

public interface HasLocalHandlers {
    Registration registerHandler(Object target);

    Registration registerHandler(Object target, HandlerConfiguration<DeserializingMessage> handlerConfiguration);
}
