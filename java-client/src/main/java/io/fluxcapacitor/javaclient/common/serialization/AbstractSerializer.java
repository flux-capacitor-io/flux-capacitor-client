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

package io.fluxcapacitor.javaclient.common.serialization;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.serialization.Converter;
import io.fluxcapacitor.javaclient.common.serialization.casting.CasterChain;
import io.fluxcapacitor.javaclient.common.serialization.casting.DefaultCasterChain;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.reflect.TypeUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.ifClass;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getRevisionNumber;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public abstract class AbstractSerializer<I> implements Serializer {
    private final CasterChain<SerializedObject<byte[]>, SerializedObject<?>> upcasterChain;
    private final CasterChain<Data<I>, Data<I>> downcasterChain;
    @Getter
    private final String format;
    private final Map<String, String> typeCasters = new ConcurrentHashMap<>();

    protected AbstractSerializer(Collection<?> casterCandidates, Converter<byte[], I> converter, String format) {
        this.upcasterChain = DefaultCasterChain.createUpcaster(casterCandidates, converter);
        this.downcasterChain = DefaultCasterChain.createDowncaster(casterCandidates, converter.getOutputType());
        this.format = format;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Data<byte[]> serialize(Object object, String format) {
        if (format == null) {
            format = this.format;
        }
        try {
            if (object instanceof Data<?> data) {
                if (data.getValue() instanceof byte[]) {
                    return (Data<byte[]>) data;
                }
                return new Data<>(serialize(data.getValue(), format).getValue(), data.getType(), data.getRevision(),
                                  format);
            }
            if (Objects.equals(this.format, format)) {
                return new Data<>(doSerialize(object), getTypeString(object), getRevisionNumber(object), format);
            } else {
                return serializeToOtherFormat(object, format);
            }
        } catch (Exception e) {
            throw new SerializationException(String.format("Could not serialize a %s (format %s)",
                                                           formatValue(object), format), e);
        }
    }

    private String formatValue(Object value) {
        return value == null ? "null" : ifClass(value) instanceof Class<?> c ? c.getName() : value.getClass().getName();
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
            List<Class<?>> children = ReflectionUtils.determineCommonAncestors((Collection<?>) object);
            if (!children.isEmpty()) {
                return TypeUtils.parameterize(type, children.getFirst());
            }
        } else if (Map.class.isAssignableFrom(type)) {
            Map<?, ?> map = (Map<?, ?>) object;
            List<Class<?>> keys = ReflectionUtils.determineCommonAncestors(map.keySet());
            if (!keys.isEmpty()) {
                List<Class<?>> values = ReflectionUtils.determineCommonAncestors(map.values());
                if (!values.isEmpty()) {
                    return TypeUtils.parameterize(type, keys.getFirst(), values.getFirst());
                }
            }
        }
        return type;
    }

    protected String asString(Type type) {
        return type.getTypeName();
    }

    protected String getTypeString(Object object) {
        return asString(getType(object));
    }

    protected abstract byte[] doSerialize(Object object) throws Exception;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <S extends SerializedObject<byte[]>> Stream<DeserializingObject<byte[], S>> deserialize(
            Stream<S> dataStream, UnknownTypeStrategy unknownTypeStrategy) {
        return upcasterChain.cast((Stream<SerializedObject<byte[]>>) dataStream)
                .map(s -> {
                    String type = s.data().getType();
                    String upcastedType = upcastType(type);
                    return Objects.equals(type, upcastedType) ? s : s.withData((Data) s.data().withType(upcastedType));
                })
                .flatMap(s -> {
                    if (!Objects.equals(format, s.data().getFormat())) {
                        return (Stream) deserializeOtherFormat(s);
                    }
                    if (!isKnownType(s.data().getType())) {
                        if (unknownTypeStrategy == UnknownTypeStrategy.FAIL) {
                            throw new DeserializationException(
                                    format("Could not deserialize object. The serialized type is unknown: %s (rev. %d)",
                                           s.data().getType(), s.data().getRevision()));
                        }
                        if (unknownTypeStrategy == UnknownTypeStrategy.IGNORE) {
                            return Stream.empty();
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
                                    throw new DeserializationException("Could not deserialize a " + s.data().getType(),
                                                                       e);
                                }
                            }));
                });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> V convert(Object value, Type type) {
        if (type == null || Object.class.equals(type)) {
            return (V) value;
        }
        return doConvert(value, type);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> V clone(Object value) {
        if (value == null || value.getClass().isPrimitive() || value instanceof String) {
            return (V) value;
        }
        if (value instanceof Collection<?> collection) {
            return switch (value) {
                case List<?> __ -> (V) new ArrayList<>(collection);
                case SortedSet<?> __ -> (V) new TreeSet<>(collection);
                case Set<?> __ -> (V) new LinkedHashSet<>(collection);
                default -> (V) new LinkedList<>(collection);
            };
        }
        if (value instanceof Map<?, ?> map) {
            return value instanceof SortedMap<?, ?> ? (V) new TreeMap<>(map) : (V) new LinkedHashMap<>(map);
        }
        return (V) doClone(value);
    }

    @Override
    public Registration registerUpcasters(Object... casterCandidates) {
        return upcasterChain.registerCasterCandidates(casterCandidates);
    }

    @Override
    public Registration registerDowncasters(Object... casterCandidates) {
        return downcasterChain.registerCasterCandidates(casterCandidates);
    }

    @Override
    public Registration registerTypeCaster(String oldType, String newType) {
        typeCasters.put(oldType, newType);
        return () -> typeCasters.remove(oldType);
    }

    @Override
    public String upcastType(String type) {
        if (type == null) {
            return null;
        }
        String result = typeCasters.get(type);
        if (result == null || Objects.equals(result, type)) {
            return type;
        }
        return upcastType(result);
    }

    @Override
    public Object downcast(Object object, int desiredRevision) {
        return downcastIntermediate(new Data<>(
                asIntermediateValue(object), asString(getType(object)),
                getRevisionNumber(object), format), desiredRevision);
    }

    @Override
    public Object downcast(Data<?> object, int desiredRevision) {
        return downcastIntermediate(new Data<>(asIntermediateValue(object.getValue()), object.getType(),
                                               object.getRevision(), format), desiredRevision);
    }

    @Nullable
    private Object downcastIntermediate(Data<I> data, int desiredRevision) {
        var result = downcasterChain.cast(Stream.of(data), desiredRevision).map(Data::getValue)
                .filter(Objects::nonNull).toList();
        return switch (result.size()) {
            case 0 -> null;
            case 1 -> result.getFirst();
            default -> result;
        };
    }

    protected abstract Object doClone(Object value);

    protected abstract <V> V doConvert(Object value, Type type);

    protected boolean isKnownType(String type) {
        return type != null && ReflectionUtils.classExists(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Stream<DeserializingObject<byte[], ?>> deserializeOtherFormat(SerializedObject<byte[]> s) {
        return Stream.of(new DeserializingObject(s, (Function<Class<?>, Object>) type -> {
            try {
                if (Object.class.equals(type)) {
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
                throw new DeserializationException("Could not deserialize a " + s.data().getType(), e);
            }
        }));
    }

    protected Stream<DeserializingObject<byte[], ?>> deserializeUnknownType(SerializedObject<?> serializedObject) {
        return Stream.empty();
    }

    protected abstract Object doDeserialize(Data<?> data, String type) throws Exception;

    protected abstract I asIntermediateValue(Object input);
}
