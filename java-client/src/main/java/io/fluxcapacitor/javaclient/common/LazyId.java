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
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.io.IOException;

@Value
@NonFinal
@AllArgsConstructor
@JsonSerialize(using = LazyId.CustomSerializer.class)
@JsonDeserialize(using = LazyId.CustomDeserializer.class)
public class LazyId {
    @NonFinal
    volatile String id;

    public LazyId() {
    }

    public String getId() {
        if (id == null) {
            synchronized (this) {
                if (id == null) {
                    id = FluxCapacitor.generateId();
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
            if (value == null) {
                gen.writeNull();
            } else {
                gen.writeString(value.getId());
            }
        }
    }

    static class CustomDeserializer extends StdScalarDeserializer<LazyId> {

        protected CustomDeserializer() {
            super(LazyId.class);
        }

        @Override
        public LazyId deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return new LazyId(p.getText());
        }
    }
}
