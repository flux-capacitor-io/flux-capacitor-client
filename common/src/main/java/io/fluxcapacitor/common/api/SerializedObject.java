package io.fluxcapacitor.common.api;

public interface SerializedObject<T, S extends SerializedObject<T, S>> {
    Data<T> data();

    S withData(Data<T> data);
}
