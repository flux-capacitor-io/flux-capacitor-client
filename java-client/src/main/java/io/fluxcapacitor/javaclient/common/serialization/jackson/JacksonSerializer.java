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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import io.fluxcapacitor.javaclient.common.serialization.AbstractSerializer;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcaster;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.UpcasterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.lang.String.format;

public class JacksonSerializer extends AbstractSerializer {
    public static final JsonMapper defaultObjectMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final ObjectMapper objectMapper;

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
        super(upcasterChain);
        this.objectMapper = objectMapper;
    }

    @Override
    protected byte[] doSerialize(Object object) throws Exception {
        return objectMapper.writeValueAsBytes(object);
    }

    @Override
    protected Object doDeserialize(byte[] bytes, Class<?> type) throws Exception {
        return objectMapper.readValue(bytes, type);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Stream<DeserializingObject<byte[], ?>> handleUnknownType(SerializedObject<byte[], ?> s) {
        return Stream.of(new DeserializingObject(s, () -> {
            try {
                return doDeserialize(s.data().getValue(), Object.class);
            } catch (Exception e) {
                throw new SerializationException(format("Could not deserialize a %s to a Map. Invalid Json?",
                                                        s.data().getType()), e);
            }
        }));
    }

}
