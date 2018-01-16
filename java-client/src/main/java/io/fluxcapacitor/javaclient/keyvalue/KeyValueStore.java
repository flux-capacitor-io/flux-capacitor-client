package io.fluxcapacitor.javaclient.keyvalue;

public interface KeyValueStore {

    void store(String key, Object value);

    <R> R get(String key);

    void delete(String key);

}
