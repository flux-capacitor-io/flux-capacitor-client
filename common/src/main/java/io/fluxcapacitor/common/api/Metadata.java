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

/**
 * Represents immutable metadata associated with a Message in the Flux platform.
 * <p>
 * {@code Metadata} is a type-safe, JSON-serializable key–value store where all values are encoded as strings. It
 * supports fluent creation, transformation, and querying, and is designed to be passed along with messages to provide
 * context such as routing keys, user identity, correlation IDs, HTTP headers, and custom tracing information.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Immutable, fluent API (e.g. {@code metadata.with("key", value)})</li>
 *   <li>Auto-serializes arbitrary objects to JSON strings using Jackson</li>
 *   <li>Supports optional, lazy deserialization via {@code get(key, Class)}</li>
 *   <li>Includes built-in support for trace propagation via {@code withTrace()}</li>
 *   <li>Provides {@link #entrySet}, {@link #containsKey}}, {@link #getOrDefault} ()}, etc.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * Metadata metadata = Metadata.of("correlationId", "1234")
 *                             .with("userId", currentUser.getId())
 *                             .withTrace("workflow", "CreateOrder");
 * }</pre>
 */
@Value
public class Metadata {
    public static JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules().addModule(new NullCollectionsAsEmptyModule())
            .disable(FAIL_ON_EMPTY_BEANS).disable(FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    Map<String, String> entries;

    /**
     * Retrieves a map of entries where the keys and values are strings.
     *
     * @return a map containing the entries with string keys and values
     */
    @JsonAnyGetter
    public Map<String, String> getEntries() {
        return entries;
    }

    /**
     * Creates a new {@code Metadata} instance with the provided key-value pairs. This method generates a non-empty
     * metadata object by associating the specified keys with their corresponding values.
     *
     * @param keyValues an alternating sequence of keys and values. The number of elements must be even, where keys are
     *                  instances of {@code Object} and corresponding values follow them.
     * @return a {@code Metadata} instance containing the specified key-value pairs.
     * @throws IllegalArgumentException if the number of key-value arguments is not even.
     */
    public static Metadata of(Object... keyValues) {
        return Metadata.empty().with(keyValues);
    }

    /**
     * Creates an empty instance of the Metadata class with no entries.
     *
     * @return a Metadata instance with no key-value pairs.
     */
    public static Metadata empty() {
        return new Metadata(emptyMap());
    }

    /**
     * Creates a new {@code Metadata} instance with a single key-value pair.
     *
     * @param key   the key to be included in the metadata, not null
     * @param value the value associated with the key, can be null
     * @return a new {@code Metadata} instance containing the provided key-value pair
     */
    public static Metadata of(Object key, Object value) {
        return Metadata.empty().with(key, value);
    }

    /**
     * Creates a new instance of {@code Metadata} populated with the given map.
     *
     * @param map the map containing key-value pairs to populate the metadata. The keys and values are expected to be
     *            convertible to strings.
     * @return a new {@code Metadata} instance containing the key-value pairs from the provided map.
     */
    public static Metadata of(Map<?, ?> map) {
        return Metadata.empty().with(map);
    }

    /**
     * Constructs a new Metadata instance with the specified map of entries. The entries map defines the key-value pairs
     * for this Metadata object.
     *
     * @param entries a map containing key-value pairs representing the metadata. Keys and values must be non-null and
     *                of type String.
     */
    @JsonCreator
    private Metadata(Map<String, String> entries) {
        this.entries = entries;
    }

    /**
     * Returns the string representation of this object, which is the string representation of the underlying entries
     * map.
     *
     * @return the string representation of the entries map
     */
    @Override
    public String toString() {
        return entries.toString();
    }

    /*
        Add
     */

    /**
     * Returns a new Metadata instance that includes all the current entries and the mappings provided in the given map.
     * If a key in the given map already exists in the current entries, its value will be overwritten.
     *
     * @param values a map containing the key-value pairs to be added or updated in the metadata
     * @return a new Metadata instance with the updated entries
     */
    public Metadata with(Map<?, ?> values) {
        Map<String, String> map = new HashMap<>(entries);
        values.forEach((key, value) -> with(key, value, map));
        return new Metadata(map);
    }

    /**
     * Creates a new instance of {@code Metadata} by combining the current metadata with the given metadata.
     *
     * @param metadata the {@code Metadata} containing entries to be added to the current instance
     * @return a new {@code Metadata} instance that includes all entries from the current instance and the provided
     * metadata
     */
    public Metadata with(Metadata metadata) {
        Map<String, String> map = new HashMap<>(entries);
        map.putAll(metadata.entries);
        return new Metadata(map);
    }

    /**
     * Creates a new {@code Metadata} instance by adding the specified key-value pairs. For each pair of values
     * provided, the first value is used as the key (converted to a string), and the second value is used as the value.
     * If an odd number of arguments is provided, an {@link IllegalArgumentException} is thrown.
     *
     * @param keyValues an alternating sequence of keys and values. Each key is converted to a string, and each value is
     *                  added as a corresponding value in the metadata. The number of arguments must be even, with each
     *                  key followed by its value.
     * @return a new {@code Metadata} instance containing the updated entries.
     * @throws IllegalArgumentException if the number of provided arguments is not even.
     */
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

    /**
     * Returns a new {@code Metadata} instance with the specified key-value pair added or updated in the current
     * entries.
     * <p>
     * If the value is null, the key is removed. If the value is an {@code Optional} that is empty, no changes are
     * made.
     *
     * @param key   the key to add or update in the metadata entries
     * @param value the value associated with the key; if null, the key will be removed; if an {@code Optional} is
     *              empty, no change occurs
     * @return a new {@code Metadata} instance with the updated entries
     */
    @SneakyThrows
    public Metadata with(Object key, Object value) {
        return new Metadata(with(key, value, new HashMap<>(entries)));
    }

    /**
     * Adds the specified key-value pair to the metadata if the key is not already present.
     *
     * @param key   the key to check and potentially add to the metadata
     * @param value the value to associate with the key if the key is absent
     * @return the current metadata instance if the key is already present, or a new metadata instance with the
     * key-value pair added if the key was absent
     */
    public Metadata addIfAbsent(Object key, Object value) {
        return containsKey(key) ? this : with(key, value);
    }

    /**
     * Adds all entries from the specified map to the current {@code Metadata}, ignoring keys that already exist. If a
     * key in the provided map already exists in this {@code Metadata}, it will be excluded from the operation.
     *
     * @param map the map containing entries to be added, unless the keys already exist in this {@code Metadata}
     * @return a new {@code Metadata} instance with the combined entries from the original and the provided map,
     * excluding entries with duplicate keys
     */
    public Metadata addIfAbsent(Map<?, ?> map) {
        map = new HashMap<>(map);
        map.keySet().removeIf(this::containsKey);
        return with(map);
    }

    /**
     * Updates a map with a given key-value pair. If the value is null, the key is removed from the map. If the value is
     * an Optional and empty, it does not modify the map. If the value is non-null, it is added to the map. Non-String
     * values are serialized into a JSON string representation.
     *
     * @param key     the key to be added or updated in the map; must not be null
     * @param value   the value to associate with the given key; can be null or an Optional
     * @param entries the map in which the key-value pair is added or updated
     * @return the updated map with the provided key-value modifications applied
     */
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
        if (value instanceof Enum<?> e) {
            value = e.name();
        }
        entries.put(keyString, value instanceof String ? (String) value : objectMapper.writeValueAsString(value));
        return entries;
    }


    /**
     * Adds a trace entry to the provided map of entries. The trace key is prefixed with "$trace." followed by the
     * string representation of the provided key. If the value is non-null, it is added to the entries map. If the value
     * is null, the entry with the trace key is removed from the map.
     *
     * @param key     the key to be prefixed for the trace entry
     * @param value   the value to be associated with the trace key; if null, the key is removed
     * @param entries the map of entries to be updated with the trace key-value pair
     * @return the updated map of entries
     */
    @SneakyThrows
    private static Map<String, String> withTrace(Object key, Object value, Map<String, String> entries) {
        return with("$trace." + key, value, entries);
    }

    /**
     * Adds a trace entry with the specified key and value to the metadata. The trace key is prefixed with "$trace.".
     *
     * @param key   the key for the trace entry, which will be prefixed with "$trace."
     * @param value the value associated with the specified key
     * @return a new Metadata instance containing the updated trace entries
     */
    @SneakyThrows
    public Metadata withTrace(Object key, Object value) {
        return new Metadata(withTrace(key, value, new HashMap<>(entries)));
    }

    /*
        Remove
     */

    /**
     * Returns a new Metadata instance without the specified key. If the given key exists in the original entries, it
     * will be removed in the resulting Metadata instance. The original Metadata object remains unmodified.
     *
     * @param key the key to be removed from the Metadata entries
     * @return a new Metadata instance with the specified key removed
     */
    public Metadata without(Object key) {
        Map<String, String> map = new HashMap<>(entries);
        map.remove(key.toString());
        return new Metadata(map);
    }

    /**
     * Returns a new instance of Metadata, excluding all entries where the provided predicate evaluates to true for the
     * entry keys.
     *
     * @param check a predicate that determines which keys should be excluded. If the predicate returns true for a key,
     *              the key-value pair will be removed.
     * @return a new Metadata object with the specified entries removed.
     */
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

    /**
     * Retrieves the value associated with the given key from the entries map.
     *
     * @param key the key whose associated value is to be returned. It should be an object, and its string
     *            representation will be used to query the map.
     * @return the value associated with the specified key, or null if the key is not present in the map.
     */
    public String get(Object key) {
        return entries.get(key.toString());
    }

    /**
     * Retrieves the value associated with the provided key, if it exists, wrapped in an Optional.
     *
     * @param key the key whose associated value is to be returned, must not be null
     * @return an Optional containing the value associated with the key, or an empty Optional if no value is found
     */
    public Optional<String> getOptionally(Object key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * Retrieves the value associated with the given key and attempts to deserialize it into the specified type.
     *
     * @param <T>  the type into which the value should be deserialized
     * @param key  the key used to look up the value
     * @param type the class object representing the type to which the value will be converted
     * @return the deserialized value if the key exists and the conversion is successful; null if the key does not exist
     * or the value is null
     * @throws IllegalStateException if deserialization fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @SneakyThrows
    public <T> T get(Object key, Class<T> type) {
        String value = get(key);
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (T) value;
        }
        if (type.isEnum()) {
            return (T) Enum.valueOf((Class<Enum>) type, value);
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException e) {
            throw new IllegalStateException(format("Failed to deserialize value %s to a %s for key %s",
                                                   value, type.getSimpleName(), key), e);
        }
    }

    /**
     * Retrieves an object associated with the given key, attempts to deserialize it to the specified type, and returns
     * it wrapped in an {@code Optional}. If the object is not present, an empty {@code Optional} is returned.
     *
     * @param <T>  the type of the object to be retrieved
     * @param key  the key used to identify the object
     * @param type the class of the type to cast the retrieved object to
     * @return an {@code Optional} containing the object if present and of the specified type, or an empty
     * {@code Optional} otherwise
     */
    public <T> Optional<T> getOptionally(Object key, Class<T> type) {
        return Optional.ofNullable(get(key, type));
    }

    /**
     * Retrieves a value associated with the given key, deserializes it to the specified type, and returns it.
     *
     * @param key  The key whose associated value is to be retrieved.
     * @param type The type reference indicating the type to which the retrieved value should be deserialized.
     * @param <T>  The type of the returned value.
     * @return The deserialized value of the specified type associated with the given key, or null if no value is found.
     * @throws IllegalStateException if an error occurs during deserialization.
     */
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

    /**
     * Retrieves an object associated with the given key, deserializes it to the specified type, and returns it wrapped
     * in an {@code Optional}. If the object is not present, an empty {@code Optional} is returned.
     *
     * @param <T>  the type of the object to be retrieved
     * @param key  the key associated with the object to retrieve
     * @param type the type reference indicating the expected type of the object
     * @return an {@link Optional} containing the object if found, or an empty {@link Optional} if not found
     */
    public <T> Optional<T> getOptionally(Object key, TypeReference<T> type) {
        return Optional.ofNullable(get(key, type));
    }

    /**
     * Retrieves the value associated with the specified key or throws an exception provided by the given error supplier
     * if the key does not exist or has a null value.
     *
     * @param key           the key whose associated value is to be returned
     * @param errorSupplier a supplier that provides the exception to be thrown if the key is not found or the value is
     *                      null
     * @param <X>           the type of exception that the error supplier provides
     * @return the value associated with the specified key
     * @throws X if the key does not exist or the associated value is null
     */
    public <X extends Throwable> String getOrThrow(Object key, Supplier<? extends X> errorSupplier) throws X {
        return Optional.ofNullable(get(key)).orElseThrow(errorSupplier);
    }

    /**
     * Retrieves a map containing only the entries from the metadata whose keys start with the prefix "$trace.".
     *
     * @return a map of trace-specific entries where keys start with "$trace."
     */
    @JsonIgnore
    public Map<String, String> getTraceEntries() {
        return entrySet().stream().filter(e -> e.getKey().startsWith("$trace."))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Checks if the specified key is present in the entries map. The key is first converted to a string and then
     * checked against the map for existence.
     *
     * @param key the key to check for presence in the entries map; must not be null
     * @return true if the entries map contains the specified key, false otherwise
     */
    public boolean containsKey(Object key) {
        return entries.containsKey(key.toString());
    }

    /**
     * Checks if the given keys are present in the internal entries.
     *
     * @param keys the keys to check for presence
     * @return {@code true} if at least one of the provided keys exists, otherwise {@code false}
     */
    public boolean containsAnyKey(Object... keys) {
        return Arrays.stream(keys).anyMatch(this::containsKey);
    }

    /**
     * Determines if the specified key-value pair exists within the data structure.
     *
     * @param key   the key to check, must not be null
     * @param value the value to check, must not be null
     * @return true if the key-value pair exists, false otherwise
     */
    public boolean contains(@NonNull Object key, @NonNull Object value) {
        Object result = value instanceof String ? get(key) : get(key, value.getClass());
        return Objects.equals(result, value);
    }

    /**
     * Checks whether the current metadata contains all entries of the specified metadata.
     *
     * @param metadata the Metadata object to compare, ensuring to check if all its entries exist within the current
     *                 metadata.
     * @return true if the current metadata contains all entries from the specified metadata, false otherwise.
     */
    public boolean contains(@NonNull Metadata metadata) {
        return entries.entrySet().containsAll(metadata.entries.entrySet());
    }

    /**
     * Retrieves the value mapped to the specified key in the entries map. If the key is not found, returns the provided
     * default value.
     *
     * @param key          the key whose associated value is to be returned
     * @param defaultValue the value to return if the key is not found in the map
     * @return the value mapped to the specified key, or the default value if the key is not found
     */
    public String getOrDefault(Object key, String defaultValue) {
        return entries.getOrDefault(key.toString(), defaultValue);
    }

    /**
     * Returns a set view of the mappings contained in this metadata object.
     *
     * @return a set of entries, where each entry represents a key-value mapping in the metadata.
     */
    public Set<Map.Entry<String, String>> entrySet() {
        return entries.entrySet();
    }
}
