package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.api.SerializedObject;
import lombok.Getter;
import lombok.ToString;

import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@ToString(exclude = "object")
public class DeserializingObject<T, S extends SerializedObject<T, S>> {
    private final S serializedObject;
    @Getter private volatile boolean deserialized;
    private final Supplier<Object> object;

    public DeserializingObject(S serializedObject, Supplier<Object> payload) {
        this.serializedObject = serializedObject;
        this.object = memoize(payload);
    }

    public Object getPayload() {
        Object result = object.get();
        deserialized = true;
        return result;
    }

    public String getType() {
        return serializedObject.data().getType();
    }

    public int getRevision() {
        return serializedObject.data().getRevision();
    }

    public S getSerializedObject() {
        return serializedObject;
    }

    @SuppressWarnings("unused")
    public Class<?> getPayloadClass() {
        try {
            return Class.forName(getType());
        } catch (ClassNotFoundException e) {
            throw new SerializationException(String.format("Failed to get the class name for a %s", getType()), e);
        }
    }
}
