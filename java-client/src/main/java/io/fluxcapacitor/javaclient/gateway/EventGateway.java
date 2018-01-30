package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.Metadata;

public interface EventGateway {

    default void publishEvent(Object payload) {
        publishEvent(payload, Metadata.empty());
    }

    void publishEvent(Object payload, Metadata metadata);

}
