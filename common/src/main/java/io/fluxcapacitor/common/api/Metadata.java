package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Delegate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

@Value
public class Metadata implements Map<String, String> {
    public static ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules().disable(FAIL_ON_EMPTY_BEANS).disable(FAIL_ON_UNKNOWN_PROPERTIES);

    @Delegate
    Map<String, String> entries;

    @JsonCreator
    private Metadata(Map<String, String> entries) {
        this.entries = new HashMap<>(entries);
    }

    public static Metadata empty() {
        return new Metadata(emptyMap());
    }

    public static Metadata from(String key, String value) {
        return new Metadata(singletonMap(key, value));
    }

    public static Metadata from(Map<String, String> map) {
        return new Metadata(map);
    }

    @SneakyThrows
    public static Metadata from(String key, Object value) {
        return new Metadata(singletonMap(key, objectMapper.writeValueAsString(value)));
    }

    @SneakyThrows
    public String put(String key, Object value) {
        return put(key, objectMapper.writeValueAsString(value));
    }

    @SneakyThrows
    public <T> T get(String key, Class<T> type) {
        return Optional.ofNullable(get(key)).map(v -> {
            try {
                return objectMapper.readValue(v, type);
            } catch (IOException e) {
                throw new IllegalStateException(String.format("Failed to deserialize value %s to a %s for key %s",
                                                              v, type.getSimpleName(), key), e);
            }
        }).orElse(null);
    }

}
