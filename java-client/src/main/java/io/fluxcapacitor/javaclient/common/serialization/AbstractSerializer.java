/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

@Slf4j
public abstract class AbstractSerializer implements Serializer {
    private final Upcaster<SerializedObject<byte[], ?>> upcasterChain;

    protected AbstractSerializer(Upcaster<SerializedObject<byte[], ?>> upcasterChain) {
        this.upcasterChain = upcasterChain;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Data<byte[]> serialize(Object object) {
        byte[] bytes;
        try {
            if (object instanceof Data<?>) {
                Data<?> data = (Data<?>) object;
                if (data.getValue() instanceof byte[]) {
                    return (Data<byte[]>) data;
                }
                return new Data<>(serialize(data.getValue()).getValue(), data.getType(), data.getRevision());
            }
            bytes = doSerialize(object);
        } catch (Exception e) {
            throw new SerializationException("Could not serialize " + object, e);
        }
        Revision revision = getRevision(object);
        Type type = getType(object);
        return new Data<>(bytes, asString(type), revision == null ? 0 : revision.value());
    }

    protected Type getType(Object object) {
        if (object == null) {
            return Void.class;
        }
        Class<?> type = object.getClass();
        if (Collection.class.isAssignableFrom(type)) {
            Collection<?> collection = (Collection<?>) object;
            Set<Type> children = collection.stream().map(this::getType).collect(Collectors.toSet());
            if (children.size() == 1) {
                return TypeUtils.parameterize(type, children.iterator().next());
            }
        } else if (Map.class.isAssignableFrom(type)) {
            Map<?, ?> map = (Map<?, ?>) object;
            Set<Type> keys = map.keySet().stream().map(this::getType).collect(Collectors.toSet());
            if (keys.size() == 1) {
                Set<Type> values = map.values().stream().map(this::getType).collect(Collectors.toSet());
                if (values.size() == 1) {
                    return TypeUtils.parameterize(type, keys.iterator().next(), values.iterator().next());
                }
            }
        }
        return type;
    }

    protected String asString(Type type) {
        return type.getTypeName();
    }

    protected Revision getRevision(Object object) {
        return object == null ? null : object.getClass().getAnnotation(Revision.class);
    }

    protected abstract byte[] doSerialize(Object object) throws Exception;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <S extends SerializedObject<byte[], S>> Stream<DeserializingObject<byte[], S>> deserialize(
            Stream<S> dataStream, boolean failOnUnknownType) {
        return upcasterChain.upcast((Stream<SerializedObject<byte[], ?>>) dataStream)
                .flatMap(s -> {
                    if (!isKnownType(s.data().getType())) {
                        if (failOnUnknownType) {
                            throw new SerializationException(
                                    format("Could not deserialize object. The serialized type is unknown: %s (rev. %d)",
                                           s.data().getType(), s.data().getRevision()));
                        }
                        return (Stream) handleUnknownType(s);
                    }
                    return (Stream) Stream.of(new DeserializingObject(s, () -> {
                        try {
                            return doDeserialize(s.data().getValue(), s.data().getType());
                        } catch (Exception e) {
                            throw new SerializationException("Could not deserialize a " + s.data().getType(), e);
                        }
                    }));
                });
    }

    protected boolean isKnownType(String type) {
        try {
            Class.forName(type);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected Stream<DeserializingObject<byte[], ?>> handleUnknownType(SerializedObject<byte[], ?> serializedObject) {
        return Stream.empty();
    }

    protected abstract Object doDeserialize(byte[] bytes, String type) throws Exception;

    protected enum NullValue {
        INSTANCE;
    }
}
