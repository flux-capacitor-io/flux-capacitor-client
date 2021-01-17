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

package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toMap;

@Value
public class Metadata {
    public static JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    Map<String, String> entries;

    @JsonAnyGetter
    public Map<String, String> getEntries() {
        return entries;
    }

    public static Metadata of(@NonNull Object... keyValues) {
        return Metadata.empty().with(keyValues);
    }

    public static Metadata empty() {
        return new Metadata(emptyMap());
    }

    public static Metadata of(String key, Object value) {
        return Metadata.empty().with(key, value);
    }

    public static Metadata of(Map<String, ?> map) {
        return Metadata.empty().with(map);
    }

    @JsonCreator
    private Metadata(Map<String, String> entries) {
        this.entries = entries;
    }

    @Override
    public String toString() {
        return entries.toString();
    }

    /*
        Add
     */

    public Metadata with(Map<String, ?> values) {
        Map<String, String> map = new HashMap<>(entries);
        values.forEach((key, value) -> with(key, value, map));
        return new Metadata(map);
    }

    public Metadata with(Metadata metadata) {
        Map<String, String> map = new HashMap<>(entries);
        map.putAll(metadata.entries);
        return new Metadata(map);
    }

    public Metadata with(@NonNull Object... keyValues) {
        if (keyValues.length % 2 == 1) {
            throw new IllegalArgumentException("Failed to create metadata for keys " + Arrays.toString(keyValues));
        }
        Map<String, String> map = new HashMap<>(entries);
        for (int i = 0; i < keyValues.length; i += 2) {
            with(keyValues[i].toString(), keyValues[i + 1], map);
        }
        return new Metadata(map);
    }

    @SneakyThrows
    public Metadata with(String key, Object value) {
        return new Metadata(with(key, value, new HashMap<>(entries)));
    }


    public Metadata addIfAbsent(String key, String value) {
        return containsKey(key) ? this : with(key, value);
    }

    @SneakyThrows
    private static Map<String, String> with(String key, Object value, Map<String, String> entries) {
        if (value instanceof Optional<?>) {
            Optional<?> optional = (Optional<?>) value;
            if (!optional.isPresent()) {
                return entries;
            }
            value = optional.get();
        }
        entries.put(key, value instanceof String ? (String) value : objectMapper.writeValueAsString(value));
        return entries;
    }


    /**
     * Adds your custom trace information to the metadata.
     * Trace metadata is passed down from message to message, similar to $traceId.
     * When message A is caused by message B, all trace metadata is copied from B to A.
     * If message C is caused by B, again all traces are copied.
     * You end up with a trace of all messages indirectly caused by your message.
     * <p>
     * Trace metadata is prefixed with "$trace.", and the CorrelatingInterceptor copies it from message to message.
     */

    @SneakyThrows
    private static Map<String, String> withTrace(String key, Object value, Map<String, String> entries) {
        return with("$trace." + key, value, entries);
    }

    @SneakyThrows
    public Metadata withTrace(String key, Object value) {
        return new Metadata(withTrace(key, value, new HashMap<>(entries)));
    }

    /*
        Remove
     */

    public Metadata without(String key) {
        Map<String, String> map = new HashMap<>(entries);
        map.remove(key);
        return new Metadata(map);
    }

    public Metadata withoutIf(Predicate<String> check) {
        Map<String, String> map = new HashMap<>(entries);
        Iterator<String> iterator = map.keySet().iterator();
        iterator.forEachRemaining(key -> {
            if (check.test(key)) {
                iterator.remove();
            }
        });
        return new Metadata(map);
    }

    /*
        Query
     */

    public String get(String key) {
        return entries.get(key);
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public <T> T get(String key, Class<T> type) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (T) value;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalStateException(format("Failed to deserialize value %s to a %s for key %s",
                    value, type.getSimpleName(), key), e);
        }
    }

    @JsonIgnore
    public Map<String, String> getTraceEntries() {
        return entrySet().stream().filter(e -> e.getKey().startsWith("$trace."))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    public boolean containsKey(String key) {
        return entries.containsKey(key);
    }

    public boolean containsAnyKey(String... keys) {
        return Arrays.stream(keys).anyMatch(entries::containsKey);
    }

    public String getOrDefault(String key, String defaultValue) {
        return entries.getOrDefault(key, defaultValue);
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return entries.entrySet();
    }
}
