/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.keyvalue;

import io.fluxcapacitor.javaclient.common.repository.Repository;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;

public class KeyValueRepository<T> implements Repository<T> {

    private final KeyValueService keyValueService;
    private final Serializer serializer;
    private final Class<? extends T> type;

    public KeyValueRepository(KeyValueService keyValueService, Serializer serializer, Class<? extends T> type) {
        this.keyValueService = keyValueService;
        this.serializer = serializer;
        this.type = type;
    }

    @Override
    public void put(Object id, T value) {
        try {
            keyValueService.putValue(id.toString(), serializer.serialize(value)).await();
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Could not store a value %s for key %s", value, id), e);
        }
    }

    @Override
    public T get(Object id) {
        byte[] result = keyValueService.getValue(id.toString());
        return result == null ? null : serializer.deserialize(result, type);
    }

    @Override
    public void delete(Object id) {
        keyValueService.deleteValue(id.toString());
    }
}
