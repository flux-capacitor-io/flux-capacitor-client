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

package io.fluxcapacitor.javaclient.common.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.common.SerializationException;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Revision;

import java.io.IOException;

public class JacksonSerializer implements Serializer {
    private final ObjectMapper objectMapper;

    public JacksonSerializer() {
        this(new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL));
    }

    public JacksonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
    @SuppressWarnings("unchecked")
    public <T> T deserialize(Data<byte[]> data) {
        try {
            return (T) objectMapper.readValue(data.getValue(), Class.forName(data.getType()));
        } catch (IOException | ClassNotFoundException e) {
            throw new SerializationException("Could not deserialize a " + data.getType(), e);
        }
    }
}
