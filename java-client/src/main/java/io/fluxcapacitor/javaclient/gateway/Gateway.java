package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Metadata;

public interface Gateway {

    Awaitable publish(Object payload, Metadata metadata);

    default Awaitable publish(Object payload) {
        return publish(payload, Metadata.empty());
    }

}
