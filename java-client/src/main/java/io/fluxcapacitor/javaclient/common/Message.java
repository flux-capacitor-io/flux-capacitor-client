package io.fluxcapacitor.javaclient.common;

import io.fluxcapacitor.common.api.Metadata;
import lombok.Value;

@Value
public class Message {
    Object payload;
    Metadata metadata;

    @SuppressWarnings("unchecked")
    public <R> R getPayload() {
        return (R) payload;
    }
}
