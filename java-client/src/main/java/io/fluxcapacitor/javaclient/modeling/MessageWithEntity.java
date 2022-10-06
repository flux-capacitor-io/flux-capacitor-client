package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.HasMessage;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;

@Value
public class MessageWithEntity implements HasMessage, HasEntity {
    Entity<?> entity;
    Object payload;

    public MessageWithEntity(Object payload, Entity<?> entity) {
        this.payload = payload;
        this.entity = entity;
    }

    @Override
    public Metadata getMetadata() {
        return payload instanceof HasMetadata ? ((HasMetadata) payload).getMetadata() : Metadata.empty();
    }

    @Override
    public Message toMessage() {
        return Message.asMessage(payload);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> R getPayload() {
        return payload instanceof HasMessage ? ((HasMessage) payload).getPayload() : (R) payload;
    }
}
