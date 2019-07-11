package io.fluxcapacitor.javaclient.common;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Wither;

@Value
@AllArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class Message {
    public static IdentityProvider identityProvider = new UuidFactory();
    
    @Wither
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
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
