package io.fluxcapacitor.javaclient.keyvalue;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultKeyValueStore implements KeyValueStore {

    private final KeyValueClient client;
    private final Serializer serializer;

    @Override
    public void store(String key, Object value, Guarantee guarantee) {
        try {
            client.putValue(key, serializer.serialize(value), guarantee).await();
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not store a value %s for key %s", value, key), e);
        }
    }

    @Override
    public <R> R get(String key) {
        try {
            Data<byte[]> value = client.getValue(key);
            return value == null ? null : serializer.deserialize(value);
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not get the value for key %s", key), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteValue(key).await();
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not delete the value at key %s", key), e);
        }
    }
}
