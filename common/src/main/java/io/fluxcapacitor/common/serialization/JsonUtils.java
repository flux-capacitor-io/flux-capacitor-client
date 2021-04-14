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

package io.fluxcapacitor.common.serialization;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.fluxcapacitor.common.FileUtils;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals;

public class JsonUtils {
    public static final JsonMapper reader = JsonMapper.builder()
            .findAndAddModules().addModule(new StripStringsModule()).addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .nodeFactory(withExactBigDecimals(true)).serializationInclusion(JsonInclude.Include.NON_NULL)
            .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, JAVA_LANG_OBJECT, PROPERTY)
            .build();

    public static final JsonMapper writer = reader.rebuild().deactivateDefaultTyping().build();

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T fromFile(String fileName) {
        return (T) reader.readValue(FileUtils.loadFile(fileName), Object.class);
    }

    @SneakyThrows
    public static <T> T fromFile(String fileName, JavaType javaType) {
        return reader.readValue(FileUtils.loadFile(fileName), javaType);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName) {
        return (T) reader.readValue(FileUtils.loadFile(referencePoint, fileName), Object.class);
    }

    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName, JavaType javaType) {
        return reader.readValue(FileUtils.loadFile(referencePoint, fileName), javaType);
    }

    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName, Class<T> type) {
        return reader.readValue(FileUtils.loadFile(referencePoint, fileName), type);
    }

    public static List<?> fromFile(String... fileNames) {
        return Arrays.stream(fileNames).map(JsonUtils::fromFile).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T fromJson(String json) {
        return (T) fromJson(json, Object.class);
    }

    @SneakyThrows
    public static <T> T fromJson(String json, Class<T> type) {
        return reader.readValue(json, type);
    }

    @SneakyThrows
    public static <T> T fromJson(String json, JavaType type) {
        return reader.readValue(json, type);
    }

    @SneakyThrows
    public static <T> T fromJson(byte[] json, Class<T> type) {
        return reader.readValue(json, type);
    }

    @SneakyThrows
    public static String asJson(Object object) {
        return writer.writeValueAsString(object);
    }

    @SneakyThrows
    public static String asPrettyJson(Object object) {
        return writer.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }

    @SneakyThrows
    public static byte[] asBytes(Object object) {
        return writer.writeValueAsBytes(object);
    }

    public static <T> T convertValue(Object fromValue, Class<? extends T> toValueType) {
        return (JsonNode.class.isAssignableFrom(toValueType) ? writer : reader)
                .convertValue(fromValue, toValueType);
    }

    @SneakyThrows
    public static JsonNode readTree(InputStream readEntity) {
        return writer.readTree(readEntity);
    }

    @SneakyThrows
    public static <T extends JsonNode> T valueToTree(Object object) {
        return writer.valueToTree(object);
    }
}
