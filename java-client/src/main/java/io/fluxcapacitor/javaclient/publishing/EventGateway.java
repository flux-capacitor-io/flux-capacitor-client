package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

import static io.fluxcapacitor.common.MessageType.EVENT;

public interface EventGateway {

    default void publish(Object event) {
        Message message = event instanceof Message ? (Message) event : new Message(event, Metadata.empty(), EVENT);
        publish(message);
    }

    default void publish(Object payload, Metadata metadata) {
        publish(new Message(payload, metadata, EVENT));
    }
    
    void publish(Message message);

    Registration registerLocalHandler(Object handler);

}
