package io.fluxcapacitor.javaclient.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;
import lombok.Value;
import org.hamcrest.Matcher;
import org.opentest4j.AssertionFailedError;

import java.util.Collection;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS;
import static java.util.stream.Collectors.toList;

public class GivenWhenThenAssertionError extends AssertionFailedError {
    public static ObjectWriter formatter = new ObjectMapper()
            .findAndRegisterModules().disable(FAIL_ON_EMPTY_BEANS)
            .disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES).writerWithDefaultPrettyPrinter();

    public GivenWhenThenAssertionError(String message) {
        super(message);
    }

    public GivenWhenThenAssertionError(String message, Throwable cause) {
        super(message, cause);
    }

    public GivenWhenThenAssertionError(String message, Object expected, Object actual) {
        super(message, formatForComparison(expected), formatForComparison(actual));
    }

    @SneakyThrows
    private static Object formatForComparison(Object expectedOrActual) {
        if (expectedOrActual instanceof Message) {
            Message message = (Message) expectedOrActual;
            Metadata metadata = message.getMetadata().withoutIf(key -> key.startsWith("$"));
            return new PayloadAndMetadata(message.getPayload(), metadata);
        }
        if (expectedOrActual instanceof Collection) {
            Collection<?> collection = (Collection<?>) expectedOrActual;
            return collection.stream().map(GivenWhenThenAssertionError::formatForComparison).collect(toList());
        }
        if (expectedOrActual instanceof Matcher<?>) {
            return expectedOrActual;
        }
        try {
            return expectedOrActual instanceof CharSequence
                    ? expectedOrActual
                    : formatter.writeValueAsString(expectedOrActual).replaceAll("\\\\n", "\n");
        } catch (Exception e) {
            return expectedOrActual;
        }
    }

    @Value
    private static class PayloadAndMetadata {
        Object payload;
        Metadata metadata;

        @Override
        public String toString() {
            try {
                return formatter.writeValueAsString(this).replaceAll("\\\\n", "\n");
            } catch (Exception e) {
                return "Message{" +
                        "payload=" + payload +
                        ", metadata=" + metadata +
                        ", payloadType=" + getPayloadType() +
                        '}';
            }
        }

        public String getPayloadType() {
            return payload.getClass().getName();
        }
    }


}
