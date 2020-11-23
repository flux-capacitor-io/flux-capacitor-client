package io.fluxcapacitor.common.serialization;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.SneakyThrows;

public class BlankStringsAsNullModule extends SimpleModule {

    @Override
    public void setupModule(SetupContext context) {
        addDeserializer(String.class, new BlankStringsToNullDeserializer());
        super.setupModule(context);
    }

    private static class BlankStringsToNullDeserializer extends JsonDeserializer<String> {
        @Override
        @SneakyThrows
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.getCurrentToken() == JsonToken.VALUE_STRING && "".equals(parser.getText())) {
                return null;
            }
            return StringDeserializer.instance.deserialize(parser, context);
        }
    }
}
