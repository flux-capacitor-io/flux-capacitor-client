package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

public interface EventGateway {

    default void publishEvent(Object event) {
        if (event instanceof Message) {
            publishEvent(((Message) event).getPayload(), ((Message) event).getMetadata());
        } else {
            publishEvent(event, Metadata.empty());
        }
    }

    void publishEvent(Object payload, Metadata metadata);

}
