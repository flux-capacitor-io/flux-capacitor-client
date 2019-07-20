package io.fluxcapacitor.javaclient.keyvalue;

import io.fluxcapacitor.common.Guarantee;

public interface KeyValueStore {

    default void store(String key, Object value) {
        store(key, value, Guarantee.SENT);
    }

    void store(String key, Object value, Guarantee guarantee);

    <R> R get(String key);

    void delete(String key);

}
