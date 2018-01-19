package io.fluxcapacitor.javaclient.keyvalue;

import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultKeyValueStore implements KeyValueStore {

    private final KeyValueClient client;
    private final Serializer serializer;

    @Override
    public void store(String key, Object value) {
        try {
            client.putValue(key, serializer.serialize(value)).await();
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not store a value %s for key %s", value, key), e);
        }
    }

    @Override
    public <R> R get(String key) {
        try {
            return serializer.deserialize(client.getValue(key));
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not get the value for key %s", key), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteValue(key);
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not delete the value at key %s", key), e);
        }
    }
}
