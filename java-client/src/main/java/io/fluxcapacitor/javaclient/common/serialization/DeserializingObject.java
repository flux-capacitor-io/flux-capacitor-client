package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.ObjectUtils.MemoizingSupplier;
import io.fluxcapacitor.common.api.SerializedObject;
import lombok.SneakyThrows;
import lombok.ToString;

import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@ToString(exclude = "object")
public class DeserializingObject<T, S extends SerializedObject<T, S>> {
    private final S serializedObject;
    private final MemoizingSupplier<Object> object;

    public DeserializingObject(S serializedObject, Supplier<Object> payload) {
        this.serializedObject = serializedObject;
        this.object = memoize(payload);
    }

    @SuppressWarnings("unchecked")
    public <V> V getPayload() {
        return (V) object.get();
    }
    
    public boolean isDeserialized() {
        return object.isCached();
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

    @SneakyThrows
    @SuppressWarnings("unused")
    public Class<?> getPayloadClass() {
        return Class.forName(getType());
    }
}