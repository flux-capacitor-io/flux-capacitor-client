package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;

public interface ResultGateway {

    default void respond(Object payload, String target, int requestId) {
        respond(payload, Metadata.empty(), target, requestId);
    }

    void respond(Object payload, Metadata metadata, String target, int requestId);

}
