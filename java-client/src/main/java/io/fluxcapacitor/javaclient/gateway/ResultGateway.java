package io.fluxcapacitor.javaclient.gateway;

import io.fluxcapacitor.common.api.Metadata;

public interface ResultGateway {

    default void respond(Object payload, String target, String requestId) {
        respond(payload, Metadata.empty(), target, requestId);
    }

    void respond(Object payload, Metadata metadata, String target, String requestId);

}
