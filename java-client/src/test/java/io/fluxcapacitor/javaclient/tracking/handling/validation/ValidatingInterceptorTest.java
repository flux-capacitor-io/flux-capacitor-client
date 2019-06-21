package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatingInterceptorTest {

    private ValidatingInterceptor subject = new ValidatingInterceptor();
    private Function<Object, DeserializingMessage> messageFactory = payload -> new DeserializingMessage(
            new DeserializingObject<>(new SerializedMessage(new Data<>("test".getBytes(), "test", 0), Metadata.empty(), "someId"),
                                      () -> payload),
            MessageType.EVENT);

    @Test
    void testWithConstraintViolations() {
        DeserializingMessage message =
                messageFactory.apply(new ConstrainedObject(null, 3, null, new ConstrainedObjectMember(false)));
        ValidationException e = assertThrows(
                ValidationException.class, 
                () -> subject.interceptHandling(m -> null, null, "test").apply(message),
                "Expected doThing() to throw, but it didn't");
        
        assertEquals(4, e.getViolations().size());
        assertTrue(e.getViolations().stream().anyMatch(v -> v.equals("custom message")));
        assertTrue(e.getViolations().stream().anyMatch(v -> v.equals("string must not be null")));
    }

    @Test
    void testWithoutConstraintViolations() {
        DeserializingMessage message =
                messageFactory.apply(new ConstrainedObject("foo", 5, "bar", new ConstrainedObjectMember(true)));
        subject.interceptHandling(m -> null, null, "test").apply(message);
    }

    @Test
    void testObjectWithoutAnnotations() {
        DeserializingMessage message = messageFactory.apply(new Object());
        subject.interceptHandling(m -> null, null, "test").apply(message);
    }

    @Value
    private static class ConstrainedObject {
        @NotNull String string;
        @Min(5) long number;
        @NotNull(message = "custom message") String customString;
        @Valid ConstrainedObjectMember member;
    }

    @Value
    private static class ConstrainedObjectMember {
        @AssertTrue boolean aBoolean;
    }

}