/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcaster;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public abstract class AbstractSerializer implements Serializer {
    private final Upcaster<SerializedObject<byte[], ?>> upcasterChain;
    @Getter
    private final String format;

    protected AbstractSerializer(Upcaster<SerializedObject<byte[], ?>> upcasterChain, String format) {
        this.upcasterChain = upcasterChain;
        this.format = format;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Data<byte[]> serialize(Object object, String format) {
        if (format == null) {
            format = this.format;
        }
        try {
            if (object instanceof Data<?>) {
                Data<?> data = (Data<?>) object;
                if (data.getValue() instanceof byte[]) {
                    return (Data<byte[]>) data;
                }
                return new Data<>(serialize(data.getValue(), format).getValue(), data.getType(), data.getRevision(),
                                  format);
            }
            if (Objects.equals(this.format, format)) {
                return new Data<>(doSerialize(object), asString(getType(object)),
                                  getRevision(object).map(Revision::value).orElse(0), format);
            } else {
                return serializeToOtherFormat(object, format);
            }
        } catch (Exception e) {
            throw new SerializationException(String.format("Could not serialize %s (format %s)", object, format), e);
        }
    }

    @SneakyThrows
    protected Data<byte[]> serializeToOtherFormat(Object object, String format) {
        if (object instanceof String) {
            return new Data<>(((String) object).getBytes(UTF_8), asString(String.class), 0, format);
        }
        if (object instanceof byte[]) {
            return new Data<>((byte[]) object, asString(byte[].class), 0, format);
        }
        if (object instanceof InputStream) {
            try (InputStream inputStream = (InputStream) object) {
                return new Data<>(inputStream.readAllBytes(), asString(byte[].class), 0, format);
            }
        }
        throw new UnsupportedOperationException();
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

    protected Optional<Revision> getRevision(Object object) {
        return Optional.ofNullable(object).map(o -> o.getClass().getAnnotation(Revision.class));
    }

    protected abstract byte[] doSerialize(Object object) throws Exception;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <S extends SerializedObject<byte[], S>> Stream<DeserializingObject<byte[], S>> deserialize(
            Stream<S> dataStream, boolean failOnUnknownType) {
        return upcasterChain.upcast((Stream<SerializedObject<byte[], ?>>) dataStream)
                .flatMap(s -> {
                    if (!Objects.equals(format, s.data().getFormat())) {
                        return (Stream) deserializeOtherFormat(s);
                    }
                    if (s.data().getType() == null) {
                        return (Stream) deserializeUnknownType(s);
                    }
                    if (!isKnownType(s.data().getType())) {
                        if (failOnUnknownType) {
                            throw new SerializationException(
                                    format("Could not deserialize object. The serialized type is unknown: %s (rev. %d)",
                                           s.data().getType(), s.data().getRevision()));
                        }
                        return (Stream) deserializeUnknownType(s);
                    }
                    return Stream.<DeserializingObject<byte[], S>>of(
                            new DeserializingObject(s, (Function<Class<?>, Object>) type -> {
                                try {
                                    return Object.class.equals(type)
                                            ? doDeserialize(s.data(), s.data().getType())
                                            : doDeserialize(s.data(), asString(type));
                                } catch (Exception e) {
                                    throw new SerializationException("Could not deserialize a " + s.data().getType(),
                                                                     e);
                                }
                            }));
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> V convert(Object value, Class<V> type) {
        if (type == null || Object.class.equals(type)) {
            return (V) value;
        }
        return doConvert(value, type);
    }

    protected abstract <V> V doConvert(Object value, Class<V> type);

    protected boolean isKnownType(String type) {
        try {
            ReflectionUtils.classForName(type);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Stream<DeserializingObject<byte[], ?>> deserializeOtherFormat(SerializedObject<byte[], ?> s) {
        return Stream.of(new DeserializingObject(s, (Function<Class<?>, Object>) type -> {
            try {
                if (Object.class.equals(type)) {
                    if (s.data().getFormat() != null && s.data().getFormat().startsWith("text/")) {
                        return new String(s.data().getValue(), UTF_8);
                    }
                    return s.data().getValue();
                }
                if (byte[].class.isAssignableFrom(type)) {
                    return s.data().getValue();
                }
                if (String.class.isAssignableFrom(type)) {
                    return new String(s.data().getValue());
                }
                if (InputStream.class.isAssignableFrom(type)) {
                    return new ByteArrayInputStream(s.data().getValue());
                }
                return doDeserialize(s.data(), asString(type));
            } catch (Exception e) {
                throw new SerializationException("Could not deserialize a " + s.data().getType(), e);
            }
        }));
    }

    protected Stream<DeserializingObject<byte[], ?>> deserializeUnknownType(
            SerializedObject<byte[], ?> serializedObject) {
        return Stream.empty();
    }

    protected abstract Object doDeserialize(Data<byte[]> data, String type) throws Exception;
}
