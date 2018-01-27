package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class DeserializingMessage {
    private final DeserializingObject<byte[], SerializedMessage> delegate;

    public Object getPayload() {
        return delegate.getObject();
    }

    public Metadata getMetadata() {
        return delegate.getSerializedObject().getMetadata();
    }

    public String getType() {
        return delegate.getSerializedObject().getData().getType();
    }

    public int getRevision() {
        return delegate.getSerializedObject().getData().getRevision();
    }

    public Class<?> getPayloadClass() {
        try {
            return Class.forName(getType());
        } catch (ClassNotFoundException e) {
            throw new SerializationException(String.format("Failed to get the class name for a %s", getType()), e);
        }
    }

    public SerializedMessage getSerializedMessage() {
        return delegate.getSerializedObject();
    }
}
