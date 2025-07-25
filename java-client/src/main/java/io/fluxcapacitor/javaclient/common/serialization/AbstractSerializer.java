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

import static io.fluxcapacitor.common.reflection.ReflectionUtils.asClass;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getRevisionNumber;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Abstract base implementation of the {@link Serializer} interface.
 *
 * <p>
 * This class provides common functionality for serialization frameworks including:
 * <ul>
 *     <li>Upcasting and downcasting of serialized data based on revision</li>
 *     <li>Support for multiple serialization formats</li>
 *     <li>Lazy deserialization with memoization</li>
 *     <li>Type upcasting via explicit mappings</li>
 * </ul>
 *
 * <p>
 * Concrete subclasses must implement the core logic for serializing and deserializing objects using a specific
 * format (e.g., JSON or Protobuf). This includes:
 * <ul>
 *     <li>{@link #doSerialize(Object)}</li>
 *     <li>{@link #doDeserialize(Data, String)}</li>
 *     <li>{@link #doClone(Object)}</li>
 *     <li>{@link #doConvert(Object, Type)}</li>
 *     <li>{@link #asIntermediateValue(Object)} for downcasting</li>
 * </ul>
 *
 * <p>
 * Thread-safe and reusable. Used throughout the Flux Capacitor framework for all (de)serialization needs.
 *
 * @param <I> the internal intermediate format (e.g., JsonNode) used during downcasting
 */
@Slf4j
public abstract class AbstractSerializer<I> implements Serializer {

    private final CasterChain<SerializedObject<byte[]>, SerializedObject<?>> upcasterChain;
    private final CasterChain<Data<I>, Data<I>> downcasterChain;
    @Getter
    private final String format;
    private final Map<String, String> typeCasters = new ConcurrentHashMap<>();

    /**
     * Constructs a new serializer with the provided caster candidates and converter.
     *
     * @param casterCandidates a collection of objects providing up/down-casting functionality
     * @param converter        a converter used to assist in interpreting serialized data
     * @param format           the default serialization format name (e.g., "json")
     */
    protected AbstractSerializer(Collection<?> casterCandidates, Converter<byte[], I> converter, String format) {
        this.upcasterChain = DefaultCasterChain.createUpcaster(casterCandidates, converter);
        this.downcasterChain = DefaultCasterChain.createDowncaster(casterCandidates, converter.getOutputType());
        this.format = format;
    }

    /**
     * Serializes the given object into a byte-based {@link Data} object, using the specified format.
     *
     * @param object the object to serialize
     * @param format the desired serialization format
     * @return a {@code Data<byte[]>} object containing the serialized bytes
     */
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
        return value == null ? "null" : asClass(value).getName();
    }

    /**
     * Converts common simple object types (e.g., String, byte[]) to the requested alternative format. Used when the
     * object cannot be serialized using the current default format.
     */
    @SneakyThrows
    protected Data<byte[]> serializeToOtherFormat(Object object, String format) {
        return switch (object) {
            case null -> new Data<>(new byte[0], null, 0, format);
            case String s -> new Data<>(s.getBytes(UTF_8), asString(String.class), 0, format);
            case byte[] bytes -> new Data<>(bytes, asString(byte[].class), 0, format);
            case InputStream inputStream -> new Data<>(inputStream.readAllBytes(), asString(byte[].class), 0, format);
            default -> throw new UnsupportedOperationException();
        };
    }

    /**
     * Determines the full type signature of the given object.
     */
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

    /**
     * Converts a {@link Type} to a readable string name.
     */
    protected String asString(Type type) {
        return type.getTypeName();
    }

    /**
     * Resolves the effective string representation of an object's type, including generic parameters if applicable.
     */
    protected String getTypeString(Object object) {
        return asString(getType(object));
    }

    /**
     * Hook for serializing the object to bytes using the primary format. Must be implemented by subclasses.
     */
    protected abstract byte[] doSerialize(Object object) throws Exception;

    /**
     * Deserializes a stream of {@link SerializedObject} values into deserialized objects. Applies upcasters, format
     * detection, and lazy deserialization as needed.
     */
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

    /**
     * Converts the given object to the specified target type.
     */
    @SuppressWarnings("unchecked")
    @Override
    public <V> V convert(Object value, Type type) {
        if (type == null || Object.class.equals(type)) {
            return (V) value;
        }
        return doConvert(value, type);
    }

    /**
     * Creates a deep copy of the object. For known types (collections, maps, etc.) a shallow copy is made; otherwise,
     * uses {@link #doClone(Object)}.
     */
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

    /**
     * Registers custom upcasters for handling older serialized formats.
     */
    @Override
    public Registration registerUpcasters(Object... casterCandidates) {
        return upcasterChain.registerCasterCandidates(casterCandidates);
    }

    /**
     * Registers custom downcasters for transforming newer object states to older revisions.
     */
    @Override
    public Registration registerDowncasters(Object... casterCandidates) {
        return downcasterChain.registerCasterCandidates(casterCandidates);
    }

    /**
     * Registers a type mapping for upcasting old type names to new ones.
     */
    @Override
    public Registration registerTypeCaster(String oldType, String newType) {
        typeCasters.put(oldType, newType);
        return () -> typeCasters.remove(oldType);
    }

    /**
     * Resolves the current type from a potentially chained upcast mapping.
     */
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

    /**
     * Downcasts a deserialized object to the desired revision number.
     */
    @Override
    public Object downcast(Object object, int desiredRevision) {
        return downcastIntermediate(new Data<>(
                asIntermediateValue(object), asString(getType(object)),
                getRevisionNumber(object), format), desiredRevision);
    }

    /**
     * Downcasts an object already in {@link Data} form.
     */
    @Override
    public Object downcast(Data<?> object, int desiredRevision) {
        return downcastIntermediate(new Data<>(asIntermediateValue(object.getValue()), object.getType(),
                                               object.getRevision(), format), desiredRevision);
    }

    /**
     * Internal helper to run the downcaster chain and extract the object(s) at the desired revision.
     */
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

    /**
     * Hook to clone a deserialized object. Subclasses must implement this if custom cloning is needed.
     */
    protected abstract Object doClone(Object value);

    /**
     * Converts a deserialized object to the desired target type. May delegate to a type mapping library.
     */
    protected abstract <V> V doConvert(Object value, Type type);

    /**
     * Checks whether a given serialized type is recognized on the classpath.
     */
    protected boolean isKnownType(String type) {
        return type != null && ReflectionUtils.classExists(type);
    }

    /**
     * Handles deserialization of objects in formats other than the current default (e.g., fallback string
     * deserialization).
     */
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

    /**
     * Hook for handling deserialization of unknown types. Subclasses can override to log or recover gracefully.
     */
    protected Stream<DeserializingObject<byte[], ?>> deserializeUnknownType(SerializedObject<?> serializedObject) {
        return Stream.empty();
    }

    /**
     * Core method to deserialize the given {@link Data} with an explicit type hint. Must be implemented by concrete
     * subclasses.
     */
    protected abstract Object doDeserialize(Data<?> data, String type) throws Exception;

    /**
     * Converts an object into its intermediate form (e.g., JsonNode) used for revision downcasting. Must be implemented
     * by concrete serializers.
     */
    protected abstract I asIntermediateValue(Object input);
}
