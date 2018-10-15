package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.Value;
import lombok.experimental.Delegate;

@Value
public class DeserializingMessage {
    private static final ThreadLocal<DeserializingMessage> current = new ThreadLocal<>();
    
    @Delegate
    DeserializingObject<byte[], SerializedMessage> delegate;
    MessageType messageType;

    public Metadata getMetadata() {
        return delegate.getSerializedObject().getMetadata();
    }

    public Message toMessage() {
        return new Message(delegate.getPayload(), getMetadata(), messageType, 
                           delegate.getSerializedObject().getMessageId());
    }
    
    public static void setCurrent(DeserializingMessage message) {
        current.set(message);
    }

    public static DeserializingMessage getCurrent() {
        return current.get();
    }
    
    public static void removeCurrent() {
        current.remove();
    }
}
