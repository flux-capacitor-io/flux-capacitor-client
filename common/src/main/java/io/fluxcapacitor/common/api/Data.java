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

import io.fluxcapacitor.common.ThrowingFunction;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.Value;
import lombok.With;

import java.beans.ConstructorProperties;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A generic container for holding a value along with serialization metadata such as type, revision, and format.
 * <p>
 * This class is used extensively throughout Flux Capacitor to represent serialized or intermediate values (e.g.:
 * {@link com.fasterxml.jackson.databind.JsonNode}), often as part of {@link SerializedMessage},
 * {@link SerializedDocument}, or key-value entries.
 *
 * @param <T> the type of the value held in this container
 */
@Value
@ToString(exclude = "value")
public class Data<T> implements SerializedObject<T> {

    /**
     * Standard media type for JSON data.
     */
    public static final String JSON_FORMAT = "application/json";

    /**
     * Custom format identifier for document data used in {@link SerializedDocument}.
     */
    public static final String DOCUMENT_FORMAT = "document";

    /**
     * Supplier for lazily retrieving the wrapped value.
     * <p>
     * This enables delayed deserialization and efficient memory use, especially for large payloads.
     */
    Supplier<T> value;

    /**
     * Fully qualified type name (typically class name) of the serialized value.
     * <p>
     * Used for type resolution and deserialization.
     */
    @With
    String type;

    /**
     * Revision number of the serialized value.
     * <p>
     * Used for versioning and upcasting.
     */
    @With
    int revision;

    /**
     * Optional format hint (e.g., {@code "application/json"} or {@code "document"}).
     * <p>
     * Used by serializers to determine how to interpret the binary data. Defaults to {@link #JSON_FORMAT}.
     */
    @With
    String format;

    /**
     * Constructs a {@code Data} instance with a fixed value and full metadata.
     */
    @ConstructorProperties({"value", "type", "revision", "format"})
    public Data(T value, String type, int revision, String format) {
        this(() -> value, type, revision, format);
    }

    /**
     * Constructs a {@code Data} instance without a specific format (defaults to JSON).
     */
    public Data(T value, String type, int revision) {
        this(value, type, revision, null);
    }

    /**
     * Constructs a {@code Data} instance with a {@link Supplier} to support lazy access.
     */
    public Data(Supplier<T> value, String type, int revision, String format) {
        this.value = value;
        this.type = type;
        this.revision = revision;
        this.format = format;
    }

    /**
     * Returns the deserialized value. This may trigger lazy loading via the supplier.
     *
     * @return the deserialized object
     */
    public T getValue() {
        return value.get();
    }

    /**
     * Returns the effective format for the data, defaulting to {@link #JSON_FORMAT} if {@code format} is null or
     * "json".
     */
    public String getFormat() {
        return format == null || "json".equals(format) ? JSON_FORMAT : format;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Data<?> data = (Data<?>) o;
        return revision == data.revision &&
                Objects.deepEquals(getValue(), data.getValue()) &&
                Objects.equals(type, data.type)
                && Objects.equals(getFormat(), data.getFormat());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), type, revision, format);
    }

    /**
     * Returns this instance (for {@link SerializedObject} compatibility).
     */
    @Override
    public Data<T> data() {
        return this;
    }

    /**
     * Replaces this instance with another {@code Data} object. This method simply returns the provided argument
     * (identity transform).
     */
    @Override
    public Data<T> withData(Data<T> data) {
        return data;
    }

    /**
     * Transforms the value using a custom mapping function, while preserving serialization metadata.
     *
     * @param mapper transformation function that may throw an exception
     * @param <M>    result type after mapping
     * @return a new {@code Data<M>} with the mapped value and original serialization metadata
     */
    @SneakyThrows
    public <M> Data<M> map(ThrowingFunction<T, M> mapper) {
        return new Data<>(mapper.apply(getValue()), type, revision, format);
    }
}
