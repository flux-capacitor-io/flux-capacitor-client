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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.search.Inverter;
import io.fluxcapacitor.common.search.JacksonInverter;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.serialization.AbstractSerializer;
import io.fluxcapacitor.javaclient.common.serialization.ContentFilter;
import io.fluxcapacitor.javaclient.common.serialization.DeserializationException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.javaclient.common.ClientUtils.getRevisionNumber;
import static java.lang.String.format;

/**
 * A concrete {@link io.fluxcapacitor.javaclient.common.serialization.Serializer} implementation based on Jackson.
 * <p>
 * This is the default serializer used in Flux Capacitor, supporting:
 * <ul>
 *     <li>Serialization and deserialization using Jackson's {@link ObjectMapper}</li>
 *     <li>Integration with upcasters/downcasters for versioned data evolution</li>
 *     <li>Intermediate representation based on {@link JsonNode} for revision tracking</li>
 *     <li>{@link DocumentSerializer} support for document store interoperability</li>
 *     <li>Type caching and memoization for performance</li>
 * </ul>
 *
 * <p>
 * You can customize or replace this serializer entirely by subclassing or injecting your own implementation of
 * {@code AbstractSerializer}.
 */
@Slf4j
public class JacksonSerializer extends AbstractSerializer<JsonNode> implements DocumentSerializer {
    /**
     * Default {@link JsonMapper} instance used for JSON serialization and deserialization.
     * <p>
     * In advanced scenarios, users may replace this field with a custom {@link JsonMapper}. However, this is generally
     * discouraged unless strictly necessary.
     * <p>
     * A better approach for customizing Jackson behavior is to provide your own modules via the Jackson
     * {@link com.fasterxml.jackson.databind.Module} SPI (ServiceLoader mechanism), which avoids overriding global
     * configuration and ensures compatibility.
     * <p>
     * <strong>Warning:</strong> This mapper is also used to construct and parse search documents.
     * Misconfiguration may result in inconsistencies in search indexing or data loss.
     */
    public static JsonMapper defaultObjectMapper = JsonUtils.writer;

    @Getter
    private final ObjectMapper objectMapper;
    @Delegate
    private final ContentFilter contentFilter;
    private final Function<String, JavaType> typeCache = memoize(this::getJavaType);
    private final Function<Type, String> typeStringCache = memoize(this::getCanonicalType);
    private final Inverter<JsonNode> inverter;

    /**
     * Constructs a default JacksonSerializer with no up/downcasters.
     */
    public JacksonSerializer() {
        this(Collections.emptyList());
    }

    /**
     * Constructs a JacksonSerializer with the given up/downcaster candidates.
     */
    public JacksonSerializer(Collection<?> casterCandidates) {
        this(defaultObjectMapper, casterCandidates);
    }

    /**
     * Constructs a JacksonSerializer with a specific {@link JsonMapper} instance.
     */
    public JacksonSerializer(JsonMapper objectMapper) {
        this(objectMapper, Collections.emptyList());
    }

    /**
     * Constructs a JacksonSerializer with an object mapper and up/downcaster candidates.
     */
    public JacksonSerializer(JsonMapper objectMapper, Collection<?> casterCandidates) {
        this(objectMapper, casterCandidates, new JacksonInverter());
    }

    /**
     * Full constructor with object mapper, caster candidates and custom document inverter.
     */
    public JacksonSerializer(JsonMapper objectMapper, Collection<?> casterCandidates, JacksonInverter inverter) {
        super(casterCandidates, inverter, Data.JSON_FORMAT);
        this.objectMapper = objectMapper;
        this.contentFilter = new JacksonContentFilter(objectMapper.copy());
        this.inverter = inverter;
    }

    /**
     * Returns a canonical string name for the given type.
     */
    @Override
    protected String asString(Type type) {
        return typeStringCache.apply(type);
    }

    /**
     * Serializes the object to a JSON byte array.
     */
    @Override
    protected byte[] doSerialize(Object object) throws Exception {
        return objectMapper.writeValueAsBytes(object);
    }

    /**
     * Deserializes a {@link Data} instance into an object of the given type using the Jackson object mapper. Supports
     * {@link JsonNode}, byte arrays and strings as serialized input.
     */
    @Override
    protected Object doDeserialize(Data<?> data, String type) throws Exception {
        return switch (data.getValue()) {
            case JsonNode v -> objectMapper.convertValue(v, typeCache.apply(type));
            case byte[] v -> objectMapper.readValue(v, typeCache.apply(type));
            case String v -> objectMapper.readValue(v, typeCache.apply(type));
            case null -> null;
            default ->
                    throw new IllegalArgumentException("Incompatible data value type: " + data.getValue().getClass());
        };
    }

    /**
     * Converts the given object into a {@link JsonNode} for use in revision downcasting.
     */
    @SneakyThrows
    @Override
    protected JsonNode asIntermediateValue(Object input) {
        return input instanceof byte[]
                ? objectMapper.readTree((byte[]) input)
                : objectMapper.convertValue(input, JsonNode.class);
    }

    /**
     * Determines whether the given type is known to the Jackson type system.
     */
    @Override
    protected boolean isKnownType(String type) {
        try {
            typeCache.apply(type);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Fallback handler for deserialization of unknown types. Attempts best-effort conversion using Jackson.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected Stream<DeserializingObject<byte[], ?>> deserializeUnknownType(SerializedObject<?> s) {
        SerializedObject<?> jsonNode =
                s.withData(new Data(s.data().getValue(), JsonNode.class.getName(), 0, getFormat()));
        return Stream.of(new DeserializingObject(jsonNode, (Function<Class<?>, Object>) type -> {
            try {
                Object serializedValue = jsonNode.data().getValue();
                return switch (serializedValue) {
                    case null -> convert(null, type);
                    case JsonNode json -> convert(json, type);
                    case byte[] bytes -> convert(objectMapper.readTree(bytes), type);
                    default -> throw new UnsupportedOperationException(
                            "Unsupported data type: " + serializedValue.getClass());
                };
            } catch (Exception e) {
                throw new DeserializationException(format("Could not deserialize a %s to a %s. Invalid json?",
                                                          type, s.data().getType()), e);
            }
        }));
    }

    /**
     * Resolves a canonical {@link JavaType} for the given string-based type name.
     */
    protected JavaType getJavaType(String type) {
        return objectMapper.getTypeFactory().constructFromCanonical(type);
    }

    /**
     * Computes the canonical string representation of a {@link Type}.
     */
    protected String getCanonicalType(Type type) {
        return objectMapper.constructType(type).toCanonical();
    }

    @Override
    public SerializedDocument toDocument(Object value, String id, String collection, Instant timestamp, Instant end,
                                         Metadata metadata) {
        return inverter.toDocument(value, getTypeString(value), getRevisionNumber(value), id, collection, timestamp,
                                   end, metadata);
    }

    @Override
    public <T> T fromDocument(SerializedDocument document) {
        return deserialize(document.getDocument());
    }

    @Override
    public <T> T fromDocument(SerializedDocument document, Class<T> type) {
        return deserialize(document.getDocument(), type);
    }

    /**
     * Converts an object into another type using Jackson’s {@link ObjectMapper}.
     */
    @Override
    public <V> V doConvert(Object value, Type type) {
        return objectMapper.convertValue(value, objectMapper.constructType(type));
    }

    /**
     * Performs a field-level clone by copying values from the original object into a new instance of the same type.
     * This first converts the value to an {@link com.fasterxml.jackson.databind.node.ObjectNode}.
     */
    @Override
    public Object doClone(Object value) {
        return ReflectionUtils.copyFields(value, doConvert(objectMapper.createObjectNode(), value.getClass()));
    }
}
