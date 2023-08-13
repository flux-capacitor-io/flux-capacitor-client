/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.fluxcapacitor.common.FileUtils;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS;

public class JsonUtils {
    public static JsonMapper writer = JsonMapper.builder()
            .findAndAddModules().addModule(new StripStringsModule()).addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(WRITE_DATES_AS_TIMESTAMPS).disable(WRITE_DURATIONS_AS_TIMESTAMPS)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES).disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

    public static JsonMapper reader = writer.rebuild()
            .activateDefaultTyping(LaissezFaireSubTypeValidator.instance, JAVA_LANG_OBJECT, PROPERTY)
            .build();


    @SneakyThrows
    public static Object fromFile(String fileName) {
        return fromFile(ReflectionUtils.getCallerClass(), fileName);
    }

    public static List<?> fromFile(String... fileNames) {
        Class<?> callerClass = ReflectionUtils.getCallerClass();
        return Arrays.stream(fileNames).map(f -> JsonUtils.fromFile(callerClass, f)).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName) {
        return (T) reader.readValue(FileUtils.loadFile(referencePoint, fileName), Object.class);
    }

    @SneakyThrows
    public static <T> T fromFile(String fileName, Class<T> type) {
        return fromFile(ReflectionUtils.getCallerClass(), fileName, type);
    }

    @SneakyThrows
    public static <T> T fromFile(String fileName, JavaType javaType) {
        return writer.readValue(FileUtils.loadFile(ReflectionUtils.getCallerClass(), fileName), javaType);
    }

    @SneakyThrows
    public static <T> T fromFile(String fileName, Function<TypeFactory, JavaType> typeFunction) {
        return writer.readValue(FileUtils.loadFile(ReflectionUtils.getCallerClass(), fileName),
                                typeFunction.apply(typeFactory()));
    }

    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName, Class<T> type) {
        JsonMapper mapper = Collection.class.isAssignableFrom(type) ? reader : writer;
        return mapper.readValue(FileUtils.loadFile(referencePoint, fileName), type);
    }

    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName, JavaType javaType) {
        return writer.readValue(FileUtils.loadFile(referencePoint, fileName), javaType);
    }

    @SneakyThrows
    public static <T> T fromFile(Class<?> referencePoint, String fileName,
                                 Function<TypeFactory, JavaType> typeFunction) {
        return writer.readValue(FileUtils.loadFile(referencePoint, fileName), typeFunction.apply(typeFactory()));
    }

    public static <T> T fromFileAs(String fileName) {
        return fromFile(ReflectionUtils.getCallerClass(), fileName);
    }

    public static Object fromFileWith(String fileName, Map<String, Object> replaceValues) {
        Object o = JsonUtils.fromFile(ReflectionUtils.getCallerClass(), fileName);
        replaceValues.forEach((path, replacement) -> ReflectionUtils.writeProperty(path, o, replacement));
        return o;
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T fromJson(String json) {
        return (T) reader.readValue(json, Object.class);
    }

    @SneakyThrows
    public static <T> T fromJson(String json, Class<T> type) {
        JsonMapper mapper = Collection.class.isAssignableFrom(type) ? reader : writer;
        return mapper.readValue(json, type);
    }

    @SneakyThrows
    public static <T> T fromJson(String json, JavaType type) {
        return writer.readValue(json, type);
    }

    @SneakyThrows
    public static <T> T fromJson(String json, Function<TypeFactory, JavaType> typeFunction) {
        return writer.readValue(json, typeFunction.apply(typeFactory()));
    }

    @SneakyThrows
    public static <T> T fromJson(byte[] json, Class<T> type) {
        JsonMapper mapper = Collection.class.isAssignableFrom(type) ? reader : writer;
        return mapper.readValue(json, type);
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
        return writer.convertValue(fromValue, toValueType);
    }

    @SneakyThrows
    public static JsonNode readTree(byte[] jsonContent) {
        return writer.readTree(jsonContent);
    }

    @SneakyThrows
    public static JsonNode readTree(String jsonContent) {
        return writer.readTree(jsonContent);
    }

    @SneakyThrows
    public static JsonNode readTree(InputStream jsonContent) {
        return writer.readTree(jsonContent);
    }

    @SneakyThrows
    public static <T extends JsonNode> T valueToTree(Object object) {
        return writer.valueToTree(object);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static <T> T merge(T value, Object update) {
        if (value == null) {
            return (T) update;
        }
        return update == null ? value
                : writer.readerForUpdating(value).readValue(convertValue(update, JsonNode.class));
    }

    public static TypeFactory typeFactory() {
        return writer.getTypeFactory();
    }
}
