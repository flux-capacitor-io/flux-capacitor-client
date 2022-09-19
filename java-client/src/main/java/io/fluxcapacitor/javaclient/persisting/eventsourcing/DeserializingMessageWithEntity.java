package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.Entity;
import lombok.Value;

@Value
public class DeserializingMessageWithEntity extends DeserializingMessage {
    Entity<?> entity;

    public DeserializingMessageWithEntity(DeserializingMessage message, Entity<?> entity) {
        super(message);
        this.entity = entity;
    }
}
