package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;
import lombok.experimental.Delegate;

@Value
public class DeserializingMessage {
    @Delegate
    DeserializingObject<byte[], SerializedMessage> delegate;

    public Metadata getMetadata() {
        return delegate.getSerializedObject().getMetadata();
    }

    public Message toMessage() {
        return new Message(delegate.getPayload(), getMetadata());
    }
}
