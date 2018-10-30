package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@AllArgsConstructor
public class Message {
    public static IdentityProvider identityProvider = new UuidFactory();
    
    @Wither
    Object payload;
    Metadata metadata;
    MessageType messageType;
    String messageId;

    public Message(Object payload, MessageType messageType) {
        this(payload, Metadata.empty(), messageType, identityProvider.nextId());
    }

    public Message(Object payload, Metadata metadata, MessageType messageType) {
        this(payload, metadata, messageType, identityProvider.nextId());
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }
}
