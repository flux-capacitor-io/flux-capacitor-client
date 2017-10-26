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

package io.fluxcapacitor.javaclient.common.serialization.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.SerializationException;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Revision;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Upcaster;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.UpcasterChain;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

public class JacksonSerializer implements Serializer {
    private static final ObjectMapper defaultObjectMapper =
            new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final ObjectMapper objectMapper;
    private final Upcaster<Data<byte[]>> upcasterChain;

    public JacksonSerializer() {
        this(Collections.emptyList());
    }

    public JacksonSerializer(Collection<Object> upcasters) {
        this(defaultObjectMapper, upcasters);
    }

    public JacksonSerializer(ObjectMapper objectMapper, Collection<Object> upcasters) {
        this(objectMapper, UpcasterChain.create(upcasters, new ObjectNodeConverter(objectMapper)));
    }

    public JacksonSerializer(Upcaster<Data<byte[]>> upcasterChain) {
        this(defaultObjectMapper, upcasterChain);
    }

    public JacksonSerializer(ObjectMapper objectMapper, Upcaster<Data<byte[]>> upcasterChain) {
        this.objectMapper = objectMapper;
        this.upcasterChain = upcasterChain;
    }

    @Override
    public Data<byte[]> serialize(Object object) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(object);
            Revision revision = object.getClass().getAnnotation(Revision.class);
            return new Data<>(bytes, object.getClass().getName(), revision == null ? 0 : revision.value());
        } catch (JsonProcessingException e) {
            throw new SerializationException("Could not serialize " + object, e);
        }
    }

    @Override
    public Stream<Object> deserialize(Stream<Data<byte[]>> dataStream) {
        return upcasterChain.upcast(dataStream).map(this::deserializeAfterUpcast);
    }

    @SuppressWarnings("unchecked")
    protected <T> T deserializeAfterUpcast(Data<byte[]> data) {
        try {
            return (T) objectMapper.readValue(data.getValue(), Class.forName(data.getType()));
        } catch (IOException | ClassNotFoundException e) {
            throw new SerializationException("Could not deserialize a " + data.getType(), e);
        }
    }

}
