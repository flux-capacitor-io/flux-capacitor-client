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

package io.fluxcapacitor.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import io.fluxcapacitor.common.serialization.StripStringsModule;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals;

public class SerializationUtils {
    public static final JsonMapper jsonMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new StripStringsModule()).addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .nodeFactory(withExactBigDecimals(true)).serializationInclusion(JsonInclude.Include.NON_NULL)
            .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, JAVA_LANG_OBJECT, PROPERTY)
            .build();

    @SneakyThrows
    public static Object deserialize(String fileName) {
        return jsonMapper.readValue(FileUtils.loadFile(fileName), Object.class);
    }

    @SneakyThrows
    public static Object deserialize(Class<?> referencePoint, String fileName) {
        return jsonMapper.readValue(FileUtils.loadFile(referencePoint, fileName), Object.class);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T deserializeRaw(String json) {
        return (T) deserializeRaw(json, Object.class);
    }

    @SneakyThrows
    public static <T> T deserializeRaw(String json, Class<T> type) {
        return jsonMapper.readValue(json, type);
    }

    @SneakyThrows
    public static <T> T deserializeRaw(String json, JavaType type) {
        return jsonMapper.readValue(json, type);
    }

    @SneakyThrows
    public static <T> T deserializeRaw(byte[] json, Class<T> type) {
        return jsonMapper.readValue(json, type);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> List<T> deserializeList(String fileName) {
        return jsonMapper.readValue(FileUtils.loadFile(fileName), List.class);
    }

    public static Object[] deserialize(String... fileNames) {
        return Arrays.stream(fileNames).map(SerializationUtils::deserialize).toArray();
    }

    @SneakyThrows
    public static String asString(Object object) {
        return jsonMapper.writeValueAsString(object);
    }

    @SneakyThrows
    public static String asPrettyString(Object object) {
        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    @SneakyThrows
    public static byte[] asBytes(Object object) {
        return jsonMapper.writeValueAsBytes(object);
    }

    public static <T> T convertValue(Object fromValue, Class<? extends T> toValueType) {
        return jsonMapper.convertValue(fromValue, toValueType);
    }

    @SneakyThrows
    public static JsonNode readTree(InputStream readEntity) {
        return jsonMapper.readTree(readEntity);
    }

    @SneakyThrows
    public static <T extends JsonNode> T valueToTree(Object object) {
        return jsonMapper.valueToTree(object);
    }
}
