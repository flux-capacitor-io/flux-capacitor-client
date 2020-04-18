package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.Delegate;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

@Value
public class Metadata implements Map<String, String> {
    public static JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Delegate
    Map<String, String> entries;

    public Metadata(@NonNull String... keyValues) {
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("Failed to create metadata for keys " + Arrays.toString(keyValues));
        }
        entries = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            entries.put(keyValues[i], keyValues[i + 1]);
        }
    }

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

    @Override
    public String toString() {
        return entries.toString();
    }
}
