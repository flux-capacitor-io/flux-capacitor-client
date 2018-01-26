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
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcaster;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Stream;

import static java.lang.String.format;

@Slf4j
public abstract class AbstractSerializer implements Serializer {
    private final Upcaster<SerializedObject<byte[], ?>> upcasterChain;

    protected AbstractSerializer(Upcaster<SerializedObject<byte[], ?>> upcasterChain) {
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

    @SuppressWarnings("unchecked")
    @Override
    public <S extends SerializedObject<byte[], S>> Stream<DeserializingObject<byte[], S>> deserialize(
            Stream<S> dataStream, boolean failOnUnknownType) {
        return upcasterChain.upcast((Stream<SerializedObject<byte[], ?>>) dataStream)
                .flatMap(s -> {
                    Class<?> type;
                    try {
                        type = classForType(s.data().getType());
                    } catch (Exception e) {
                        if (failOnUnknownType) {
                            throw new SerializationException(
                                    format("Could not deserialize object. The serialized type is unknown: %s (rev. %d)",
                                           s.data().getType(), s.data().getRevision()), e);
                        }
                        return (Stream) handleUnknownType(s);
                    }
                    return (Stream) Stream.of(new DeserializingObject(s, () -> {
                        try {
                            return doDeserialize(s.data().getValue(), type);
                        } catch (Exception e) {
                            throw new SerializationException("Could not deserialize a " + s.data().getType(), e);
                        }
                    }));
                });
    }

    protected Class<?> classForType(String type) throws Exception {
        return Class.forName(type);
    }

    protected Stream<DeserializingObject<byte[], ?>> handleUnknownType(SerializedObject<byte[], ?> serializedObject) {
        return Stream.empty();
    }

    protected abstract Object doDeserialize(byte[] bytes, Class<?> type) throws Exception;
}
