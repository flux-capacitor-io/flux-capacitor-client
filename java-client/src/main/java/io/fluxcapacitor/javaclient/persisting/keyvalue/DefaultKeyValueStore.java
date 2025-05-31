/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.persisting.keyvalue;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import lombok.AllArgsConstructor;

import java.util.concurrent.TimeUnit;

/**
 * The default implementation of the {@link KeyValueStore} interface that provides operations for storing, retrieving,
 * and deleting key-value pairs using a {@link KeyValueClient} and a {@link Serializer}. This class handles
 * serialization and deserialization of values and ensures that operations conform to the guarantees provided by the
 * client.
 */
@AllArgsConstructor
public class DefaultKeyValueStore implements KeyValueStore {

    private final KeyValueClient client;
    private final Serializer serializer;

    @Override
    public void store(String key, Object value, Guarantee guarantee) {
        try {
            client.putValue(key, serializer.serialize(value), guarantee).get();
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
            client.deleteValue(key).get();
        } catch (Exception e) {
            throw new KeyValueStoreException(String.format("Could not delete the value at key %s", key), e);
        }
    }
}
