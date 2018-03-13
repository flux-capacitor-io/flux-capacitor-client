package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

public interface EventGateway {

    default void publish(Object event) {
        if (event instanceof Message) {
            publish(((Message) event).getPayload(), ((Message) event).getMetadata());
        } else {
            publish(event, Metadata.empty());
        }
    }

    void publish(Object payload, Metadata metadata);

}
