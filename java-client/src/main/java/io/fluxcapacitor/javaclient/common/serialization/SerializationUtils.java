package io.fluxcapacitor.javaclient.common.serialization;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import io.fluxcapacitor.javaclient.common.FileUtils;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import lombok.SneakyThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY;
import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.JAVA_LANG_OBJECT;

public class SerializationUtils {
    public static final JsonMapper jsonMapper = JacksonSerializer.defaultObjectMapper.rebuild()
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
