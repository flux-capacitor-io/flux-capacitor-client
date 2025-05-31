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

package io.fluxcapacitor.javaclient.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.io.IOException;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * A lazily initialized identifier that serializes as a plain string.
 * <p>
 * Unlike using Lombok's lazy getters, {@code LazyId} ensures the ID is generated only once and remains stable even
 * after calling {@code .toBuilder()} on the enclosing object. This makes it useful in scenarios where the ID should be
 * deterministically generated only once per logical instance, including deserialization and cloning scenarios.
 * <p>
 * If the ID is not provided directly, a supplier (e.g., {@code FluxCapacitor::generateId}) can be used to compute it
 * lazily on first access. The ID is then cached and reused.
 */
@Value
@NonFinal
@JsonSerialize(using = LazyId.CustomSerializer.class)
@JsonDeserialize(using = LazyId.CustomDeserializer.class)
public class LazyId {
    /**
     * The string value of the identifier. May be {@code null} if not yet computed.
     */
    @NonFinal
    volatile String id;

    /**
     * Indicates whether the ID has already been computed.
     */
    @NonFinal
    @EqualsAndHashCode.Exclude
    volatile boolean computed;

    /**
     * The supplier used to generate the ID when needed. May be {@code null} if the ID is provided directly.
     */
    @EqualsAndHashCode.Exclude
    Supplier<String> supplier;

    /**
     * Constructs a new {@code LazyId} with a default ID supplier using {@code FluxCapacitor::generateId}.
     */
    public LazyId() {
        this(FluxCapacitor::generateId);
    }

    /**
     * Constructs a new {@code LazyId} with the specified supplier. The ID will be computed when accessed.
     *
     * @param supplier the supplier used to generate the ID
     */
    public LazyId(Supplier<String> supplier) {
        this.supplier = requireNonNull(supplier);
    }

    /**
     * Constructs a {@code LazyId} with a known ID value. No computation will occur.
     *
     * @param id the ID value (converted to string if non-null)
     */
    public LazyId(Object id) {
        this.id = id == null ? null : id.toString();
        this.computed = true;
        this.supplier = null;
    }

    private String getId() {
        if (!computed) {
            synchronized (this) {
                if (!computed) {
                    id = supplier.get();
                    computed = true;
                }
            }
        }
        return id;
    }

    /**
     * Returns the ID value as a string.
     */
    @Override
    public String toString() {
        return getId();
    }

    /**
     * Custom Jackson serializer for {@code LazyId}. Serializes the resolved ID as a plain string.
     */
    static class CustomSerializer extends StdScalarSerializer<LazyId> {

        protected CustomSerializer() {
            super(LazyId.class);
        }

        @Override
        public void serialize(LazyId value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            String s = value == null ? null : value.getId();
            if (s == null) {
                gen.writeNull();
            } else {
                gen.writeString(s);
            }
        }
    }

    /**
     * Custom Jackson deserializer for {@code LazyId}. Constructs a {@code LazyId} from a string.
     */
    static class CustomDeserializer extends StdScalarDeserializer<LazyId> {

        protected CustomDeserializer() {
            super(LazyId.class);
        }

        @Override
        public LazyId getNullValue(DeserializationContext ctxt) {
            return new LazyId((Object) null);
        }

        @Override
        public LazyId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new LazyId(p.getText());
        }
    }
}
