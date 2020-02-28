package io.fluxcapacitor.javaclient.persisting.keyvalue;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.TimeUnit;

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
    public boolean storeIfAbsent(String key, Object value) {
        try {
            return client.putValueIfAbsent(key, serializer.serialize(value)).get(5, TimeUnit.SECONDS);
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
