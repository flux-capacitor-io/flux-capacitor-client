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
    public byte[] serialize(Object object) {
        try {
            return objectMapper.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Could not serialize " + object, e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<? extends T> type) {
        try {
            return objectMapper.readValue(data, type);
        } catch (IOException e) {
            throw new SerializationException("Could not deserialize a " + type, e);
        }
    }
}
