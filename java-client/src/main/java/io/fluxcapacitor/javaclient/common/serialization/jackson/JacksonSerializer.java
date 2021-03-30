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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import io.fluxcapacitor.common.serialization.StripStringsModule;
import io.fluxcapacitor.javaclient.common.serialization.AbstractSerializer;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcaster;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.UpcasterChain;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.lang.String.format;

public class JacksonSerializer extends AbstractSerializer {
    public static JsonMapper defaultObjectMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new StripStringsModule()).addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .nodeFactory(withExactBigDecimals(true)).serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private final ObjectMapper objectMapper;
    private final Function<String, JavaType> typeCache = memoize(this::getJavaType);
    private final Function<Type, String> typeStringCache = memoize(this::getCanonicalType);

    public JacksonSerializer() {
        this(Collections.emptyList());
    }

    public JacksonSerializer(Collection<?> upcasters) {
        this(defaultObjectMapper, upcasters);
    }

    public JacksonSerializer(ObjectMapper objectMapper) {
        this(objectMapper, Collections.emptyList());
    }

    public JacksonSerializer(ObjectMapper objectMapper, Collection<?> upcasters) {
        this(objectMapper, UpcasterChain.create(upcasters, new ObjectNodeConverter(objectMapper)));
    }

    public JacksonSerializer(ObjectMapper objectMapper, Upcaster<SerializedObject<byte[], ?>> upcasterChain) {
        super(upcasterChain, "json");
        this.objectMapper = objectMapper;
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
    protected Object doDeserialize(byte[] bytes, String type) throws Exception {
        return objectMapper.readValue(bytes, typeCache.apply(type));
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
    protected Stream<DeserializingObject<byte[], ?>> handleUnknownType(SerializedObject<byte[], ?> s) {
        return Stream.of(new DeserializingObject(s, (Function<Class<?>, Object>) type -> {
            try {
                return convert(objectMapper.readTree(s.data().getValue()), type);
            } catch (Exception e) {
                throw new SerializationException(format("Could not deserialize a %s to a JsonNode. Invalid Json?",
                        s.data().getType()), e);
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
    public <V> V doConvert(Object value, Class<V> type) {
        return objectMapper.convertValue(value, type);
    }
}
