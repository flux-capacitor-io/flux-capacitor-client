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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.common.search.Document;
import io.fluxcapacitor.common.search.Inverter;
import io.fluxcapacitor.common.search.JacksonInverter;
import io.fluxcapacitor.common.serialization.JsonUtils;
import io.fluxcapacitor.javaclient.common.serialization.AbstractSerializer;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.casting.Caster;
import io.fluxcapacitor.javaclient.common.serialization.casting.CasterChain;
import io.fluxcapacitor.javaclient.common.serialization.casting.Converter;
import io.fluxcapacitor.javaclient.persisting.search.DocumentSerializer;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.lang.String.format;

@Slf4j
public class JacksonSerializer extends AbstractSerializer<JsonNode> implements DocumentSerializer {
    public static JsonMapper defaultObjectMapper = JsonUtils.writer;

    @Getter
    private final ObjectMapper objectMapper;
    private final ObjectMapper filterContentObjectMapper;
    private final Function<String, JavaType> typeCache = memoize(this::getJavaType);
    private final Function<Type, String> typeStringCache = memoize(this::getCanonicalType);
    private final Caster<Data<JsonNode>> jsonNodeUpcaster;
    private final Inverter<JsonNode> inverter;

    public JacksonSerializer() {
        this(Collections.emptyList());
    }

    public JacksonSerializer(Collection<?> casterCandidates) {
        this(defaultObjectMapper, casterCandidates);
    }

    public JacksonSerializer(ObjectMapper objectMapper) {
        this(objectMapper, Collections.emptyList());
    }

    public JacksonSerializer(ObjectMapper objectMapper, Collection<?> casterCandidates) {
        this(objectMapper, casterCandidates, new ObjectNodeConverter(objectMapper));
    }

    public JacksonSerializer(ObjectMapper objectMapper, Collection<?> casterCandidates, Converter<JsonNode> converter) {
        super(casterCandidates, converter, "application/json");
        this.objectMapper = objectMapper;
        this.filterContentObjectMapper = FilteringSerializer.installSerializer(objectMapper.copy());
        this.jsonNodeUpcaster = CasterChain.create(casterCandidates, JsonNode.class, false);
        this.inverter = new JacksonInverter(objectMapper);
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
    protected Object doDeserialize(Data<byte[]> data, String type) throws Exception {
        return objectMapper.readValue(data.getValue(), typeCache.apply(type));
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
    protected Stream<DeserializingObject<byte[], ?>> deserializeUnknownType(SerializedObject<byte[], ?> s) {
        SerializedObject<byte[], ?> jsonNode =
                s.withData(new Data<>(s.data().getValue(), JsonNode.class.getName(), 0, getFormat()));
        return Stream.of(new DeserializingObject(jsonNode, (Function<Class<?>, Object>) type -> {
            try {
                return convert(objectMapper.readTree(jsonNode.data().getValue()), type);
            } catch (Exception e) {
                throw new SerializationException(format("Could not deserialize a %s to a %s. Invalid json?",
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
    public Document toDocument(Object value, String id, String collection, Instant timestamp, Instant end) {
        return inverter.toDocument(serialize(value), id, collection, timestamp, end);
    }

    @Override
    public <T> T fromDocument(Document document) {
        JsonNode jsonNode = inverter.fromDocument(document);
        return jsonNodeUpcaster.cast(Stream.of(new Data<>(
                        jsonNode, document.getType(), document.getRevision(), getFormat()))).findFirst()
                .<T>map(d -> objectMapper.convertValue(d.getValue(), typeCache.apply(d.getType()))).orElse(null);
    }

    @Override
    public <T> T fromDocument(Document document, Class<T> type) {
        JsonNode jsonNode = inverter.fromDocument(document);
        return jsonNodeUpcaster.cast(Stream.of(new Data<>(
                        jsonNode, document.getType(), document.getRevision(), getFormat()))).findFirst()
                .map(d -> objectMapper.convertValue(d.getValue(), type)).orElse(null);
    }

    @Override
    public <V> V doConvert(Object value, Class<V> type) {
        return objectMapper.convertValue(value, type);
    }

    @Override
    public Object doClone(Object value) {
        return ReflectionUtils.copyFields(value, doConvert(objectMapper.createObjectNode(), value.getClass()));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T filterContent(T value, User viewer) {
        try {
            return viewer.apply(() -> filterContentObjectMapper.convertValue(value, (Class<T>) value.getClass()));
        } catch (Exception e) {
            log.warn("Failed to filter content (type {}) for viewer {}", value.getClass(), viewer, e);
            return value;
        }
    }
}
