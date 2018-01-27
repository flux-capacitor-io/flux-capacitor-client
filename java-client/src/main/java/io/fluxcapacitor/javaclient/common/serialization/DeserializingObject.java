package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.api.SerializedObject;
import lombok.Value;

import java.util.function.Supplier;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

@Value
public class DeserializingObject<T, S extends SerializedObject<T, S>> {
    private final S serializedObject;
    private final Supplier<Object> object;

    public DeserializingObject(S serializedObject, Supplier<Object> object) {
        this.serializedObject = serializedObject;
        this.object = memoize(object);
    }

    public Object getObject() {
        return object.get();
    }
}
