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

@Value
@NonFinal
@JsonSerialize(using = LazyId.CustomSerializer.class)
@JsonDeserialize(using = LazyId.CustomDeserializer.class)
public class LazyId {
    @NonFinal
    volatile String id;
    @NonFinal
    @EqualsAndHashCode.Exclude
    volatile boolean computed;

    @EqualsAndHashCode.Exclude
    Supplier<String> supplier;

    public LazyId() {
        this(FluxCapacitor::generateId);
    }

    public LazyId(Supplier<String> supplier) {
        this.supplier = requireNonNull(supplier);
    }

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

    @Override
    public String toString() {
        return getId();
    }

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
