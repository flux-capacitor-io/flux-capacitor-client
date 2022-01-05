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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Converter;
import lombok.AllArgsConstructor;

import java.io.IOException;

@AllArgsConstructor
public class ObjectNodeConverter implements Converter<JsonNode> {

    private final ObjectMapper objectMapper;

    @Override
    public JsonNode convert(byte[] bytes) {
        try {
            return objectMapper.readTree(bytes);
        } catch (IOException e) {
            throw new SerializationException("Could not read JsonNode from byte[]", e);
        }
    }

    @Override
    public byte[] convertBack(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new SerializationException("Could not write byte[] from JsonNode: " + value, e);
        }
    }

    @Override
    public Class<JsonNode> getDataType() {
        return JsonNode.class;
    }
}
