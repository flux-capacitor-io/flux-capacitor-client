package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.api.Metadata;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class Message {
    Object payload;
    Metadata metadata;

    public Message(Object payload) {
        this(payload, Metadata.empty());
    }

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }
}
