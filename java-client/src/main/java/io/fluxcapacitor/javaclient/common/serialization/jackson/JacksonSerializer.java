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

@Slf4j
public class JacksonSerializer extends AbstractSerializer<JsonNode> implements DocumentSerializer {
    public static JsonMapper defaultObjectMapper = JsonUtils.writer;

    @Getter
    private final ObjectMapper objectMapper;
    @Delegate
    private final ContentFilter contentFilter;
    private final Function<String, JavaType> typeCache = memoize(this::getJavaType);
    private final Function<Type, String> typeStringCache = memoize(this::getCanonicalType);
    private final Inverter<JsonNode> inverter;

    public JacksonSerializer() {
        this(Collections.emptyList());
    }

    public JacksonSerializer(Collection<?> casterCandidates) {
        this(defaultObjectMapper, casterCandidates);
    }

    public JacksonSerializer(JsonMapper objectMapper) {
        this(objectMapper, Collections.emptyList());
    }

    public JacksonSerializer(JsonMapper objectMapper, Collection<?> casterCandidates) {
        this(objectMapper, casterCandidates, new JacksonInverter());
    }

    public JacksonSerializer(JsonMapper objectMapper, Collection<?> casterCandidates, JacksonInverter inverter) {
        super(casterCandidates, inverter, Data.JSON_FORMAT);
        this.objectMapper = objectMapper;
        this.contentFilter = new JacksonContentFilter(objectMapper.copy());
        this.inverter = inverter;
    }

    @Override
    protected String asString(Type type) {
        return typeStringCache.apply(type);
    }

    @Override
    protected byte[] doSerialize(Object object) throws Exception {
        return objectMapper.writeValueAsBytes(object);
    }

    @Override
    protected Object doDeserialize(Data<?> data, String type) throws Exception {
        return switch (data.getValue()) {
            case JsonNode v -> objectMapper.convertValue(v, typeCache.apply(type));
            case byte[] v -> objectMapper.readValue(v, typeCache.apply(type));
            case String v -> objectMapper.readValue(v, typeCache.apply(type));
            case null -> null;
            default -> throw new IllegalArgumentException("Incompatible data value type: " + data.getValue().getClass());
        };
    }

    @SneakyThrows
    @Override
    protected JsonNode asIntermediateValue(Object input) {
        return input instanceof byte[]
                ? objectMapper.readTree((byte[]) input)
                : objectMapper.convertValue(input, JsonNode.class);
    }

    @Override
    protected boolean isKnownType(String type) {
        try {
            typeCache.apply(type);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected Stream<DeserializingObject<byte[], ?>> deserializeUnknownType(SerializedObject<?, ?> s) {
        SerializedObject<?, ?> jsonNode =
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

    protected JavaType getJavaType(String type) {
        return objectMapper.getTypeFactory().constructFromCanonical(type);
    }

    protected String getCanonicalType(Type type) {
        return objectMapper.constructType(type).toCanonical();
    }

    @Override
    public SerializedDocument toDocument(Object value, String id, String collection, Instant timestamp, Instant end) {
        return inverter.toDocument(value, getTypeString(value), getRevisionNumber(value), id, collection, timestamp,
                                   end);
    }

    @Override
    public <T> T fromDocument(SerializedDocument document) {
        return deserialize(document.getDocument());
    }

    @Override
    public <T> T fromDocument(SerializedDocument document, Class<T> type) {
        return deserialize(document.getDocument(), type);
    }

    @Override
    public <V> V doConvert(Object value, Class<V> type) {
        return objectMapper.convertValue(value, type);
    }

    @Override
    public Object doClone(Object value) {
        return ReflectionUtils.copyFields(value, doConvert(objectMapper.createObjectNode(), value.getClass()));
    }
}
