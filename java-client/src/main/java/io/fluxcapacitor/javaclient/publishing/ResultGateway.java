package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

public interface ResultGateway {

    default void respond(Object response, String target, int requestId) {
        if (response instanceof Message) {
            respond(((Message) response).getPayload(), ((Message) response).getMetadata(), target, requestId);
        } else {
            respond(response, Metadata.empty(), target, requestId);
        }
    }

    void respond(Object payload, Metadata metadata, String target, int requestId);

}
