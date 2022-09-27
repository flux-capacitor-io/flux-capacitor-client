package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import lombok.Value;

@Value
public class DeserializingMessageWithEntity extends DeserializingMessage {
    Entity<?> entity;

    public DeserializingMessageWithEntity(DeserializingMessage message, Entity<?> entity) {
        super(message);
        this.entity = entity;
    }
}
