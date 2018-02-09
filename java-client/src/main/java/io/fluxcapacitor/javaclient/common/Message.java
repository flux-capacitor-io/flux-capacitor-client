package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class Message {
    Object payload;
    Metadata metadata;
    MessageType messageType;

    public Message(Object payload, MessageType messageType) {
        this(payload, Metadata.empty(), messageType);
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }
}
