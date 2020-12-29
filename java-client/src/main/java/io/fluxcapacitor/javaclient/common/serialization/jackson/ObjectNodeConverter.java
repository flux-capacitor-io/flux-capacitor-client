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
import com.github.fge.jsonpatch.JsonPatch;
import io.fluxcapacitor.common.api.SerializedObject;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import io.fluxcapacitor.javaclient.common.serialization.upcasting.Converter;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.function.Supplier;

@AllArgsConstructor
public class ObjectNodeConverter implements Converter<JsonNode> {

    private final ObjectMapper objectMapper;

    @Override
    public JsonNode convert(byte[] bytes) {
        try {
            return (JsonNode) objectMapper.readTree(bytes);
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
    public boolean canApplyPatch(Class<?> type) {
        return JsonPatch.class.isAssignableFrom(type);
    }

    @Override
    public Supplier<?> applyPatch(SerializedObject<JsonNode, ?> s, Supplier<?> o, Class<?> type) {
        if (type.equals(JsonPatch.class)) {
            return () -> applyJsonPatch(s, o);
        }
        return o;
    }

    @SneakyThrows
    private JsonNode applyJsonPatch(SerializedObject<JsonNode, ?> s, Supplier<?> o) {
        return ((JsonPatch) o.get()).apply(s.data().getValue());
    }

    @Override
    public Class<JsonNode> getDataType() {
        return JsonNode.class;
    }
}
