package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatingInterceptorTest {

    private ValidatingInterceptor subject = new ValidatingInterceptor();
    private Function<Object, DeserializingMessage> messageFactory = payload -> new DeserializingMessage(
            new DeserializingObject<>(new SerializedMessage(new Data<>(
                    "test".getBytes(), "test", 0), Metadata.empty(), "someId",
                                                            Clock.systemUTC().millis()), () -> payload), EVENT);

    @Test
    void testWithConstraintViolations() {
        DeserializingMessage message = messageFactory.apply(new ConstrainedObject(null));
        assertThrows(ValidationException.class,
                () -> subject.interceptHandling(m -> null, null, "test").apply(message));
    }

    @Test
    void testWithoutConstraintViolations() {
        DeserializingMessage message = messageFactory.apply(new ConstrainedObject("foo"));
        subject.interceptHandling(m -> null, null, "test").apply(message);
    }

    @Value
    private static class ConstrainedObject {
        @NotNull String aString;
    }
    
}