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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Revision;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcaster;

import java.util.stream.Stream;

public abstract class AbstractSerializer implements Serializer {
    private final Upcaster<Data<byte[]>> upcasterChain;

    protected AbstractSerializer(Upcaster<Data<byte[]>> upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

    @Override
    public Data<byte[]> serialize(Object object) {
        byte[] bytes;
        try {
            bytes = doSerialize(object);
        } catch (Exception e) {
            throw new SerializationException("Could not serialize " + object, e);
        }
        Revision revision = object.getClass().getAnnotation(Revision.class);
        return new Data<>(bytes, object.getClass().getName(), revision == null ? 0 : revision.value());
    }

    protected abstract byte[] doSerialize(Object object) throws Exception;

    @Override
    public Stream<Object> deserialize(Stream<Data<byte[]>> dataStream) {
        return upcasterChain.upcast(dataStream).map(data -> {
            try {
                return doDeserialize(data.getValue(), Class.forName(data.getType()));
            } catch (Exception e) {
                throw new SerializationException("Could not deserialize a " + data.getType(), e);
            }
        });
    }

    protected abstract <T> T doDeserialize(byte[] bytes, Class<? extends T> type) throws Exception;
}
