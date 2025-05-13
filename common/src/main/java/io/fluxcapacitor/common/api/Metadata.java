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

package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.fluxcapacitor.common.serialization.NullCollectionsAsEmptyModule;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

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

    public static Metadata of(Object... keyValues) {
        return Metadata.empty().with(keyValues);
    }

    public static Metadata empty() {
        return new Metadata(emptyMap());
    }

    public static Metadata of(Object key, Object value) {
        return Metadata.empty().with(key, value);
    }

    public static Metadata of(Map<?, ?> map) {
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

    public Metadata with(Map<?, ?> values) {
        Map<String, String> map = new HashMap<>(entries);
        values.forEach((key, value) -> with(key, value, map));
        return new Metadata(map);
    }

    public Metadata with(Metadata metadata) {
        Map<String, String> map = new HashMap<>(entries);
        map.putAll(metadata.entries);
        return new Metadata(map);
    }

    public Metadata with(Object... keyValues) {
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
    public Metadata with(Object key, Object value) {
        return new Metadata(with(key, value, new HashMap<>(entries)));
    }

    public Metadata addIfAbsent(Object key, Object value) {
        return containsKey(key) ? this : with(key, value);
    }

    public Metadata addIfAbsent(Map<?, ?> map) {
        map = new HashMap<>(map);
        map.keySet().removeIf(this::containsKey);
        return with(map);
    }

    @SneakyThrows
    private static Map<String, String> with(@NonNull Object key, Object value, Map<String, String> entries) {
        String keyString = key.toString();
        if (value == null) {
            entries.remove(keyString);
            return entries;
        }
        if (value instanceof Optional<?> optional) {
            if (optional.isEmpty()) {
                return entries;
            }
            value = optional.get();
        }
        entries.put(keyString, value instanceof String ? (String) value : objectMapper.writeValueAsString(value));
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
    private static Map<String, String> withTrace(Object key, Object value, Map<String, String> entries) {
        return with("$trace." + key, value, entries);
    }

    @SneakyThrows
    public Metadata withTrace(Object key, Object value) {
        return new Metadata(withTrace(key, value, new HashMap<>(entries)));
    }

    /*
        Remove
     */

    public Metadata without(Object key) {
        Map<String, String> map = new HashMap<>(entries);
        map.remove(key.toString());
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

    public String get(Object key) {
        return entries.get(key.toString());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public <T> T get(Object key, Class<T> type) {
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

    @SneakyThrows
    public <T> T get(Object key, TypeReference<T> type) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalStateException(format("Failed to deserialize value %s to a %s for key %s",
                                                   value, type, key), e);
        }
    }

    public <X extends Throwable> String getOrThrow(Object key, Supplier<? extends X> errorSupplier) throws X {
        return Optional.ofNullable(get(key)).orElseThrow(errorSupplier);
    }

    @JsonIgnore
    public Map<String, String> getTraceEntries() {
        return entrySet().stream().filter(e -> e.getKey().startsWith("$trace."))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    public boolean containsKey(Object key) {
        return entries.containsKey(key.toString());
    }

    public boolean containsAnyKey(Object... keys) {
        return Arrays.stream(keys).anyMatch(this::containsKey);
    }

    public boolean contains(@NonNull Object key, @NonNull Object value) {
        Object result = value instanceof String ? get(key) : get(key, value.getClass());
        return Objects.equals(result, value);
    }

    public boolean contains(@NonNull Metadata metadata) {
        return entries.entrySet().containsAll(metadata.entries.entrySet());
    }

    public String getOrDefault(Object key, String defaultValue) {
        return entries.getOrDefault(key.toString(), defaultValue);
    }

    public Set<Map.Entry<String, String>> entrySet() {
        return entries.entrySet();
    }
}
