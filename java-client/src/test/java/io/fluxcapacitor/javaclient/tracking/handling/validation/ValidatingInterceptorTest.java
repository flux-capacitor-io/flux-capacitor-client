package io.fluxcapacitor.javaclient.tracking.handling.validation;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatingInterceptorTest {

    private ValidatingInterceptor subject = new ValidatingInterceptor();
    private Function<Object, DeserializingMessage> messageFactory = payload -> new DeserializingMessage(
            new DeserializingObject<>(new SerializedMessage(new Data<>(
                    "test".getBytes(), "test", 0), Metadata.empty(), "someId",
                                                            Clock.systemUTC().millis()),
                                      () -> payload),
            MessageType.EVENT);

    @Test
    void testWithConstraintViolations() {
        DeserializingMessage message =
                messageFactory.apply(ConstrainedObject.builder()
                                             .aString(null).aNumber(3).aCustomString(null)
                                             .member(new ConstrainedObjectMember(false))
                                             .aList("")
                        .anotherList(new ConstrainedObjectMember(false))
                                             .build());
        ValidationException e = assertThrows(
                ValidationException.class,
                () -> subject.interceptHandling(m -> null, null, "test").apply(message),
                "Expected doThing() to throw, but it didn't");

        assertEquals(6, e.getViolations().size());
        assertTrue(e.getViolations().stream().anyMatch(v -> v.equals("member.aBoolean must be true")));
        assertEquals("aBoolean must be true\n"
                             + "aList element must not be blank\n"
                             + "aNumber must be greater than or equal to 5\n"
                             + "aString must not be null\n"
                             + "custom message\n"
                             + "member aBoolean must be true", e.getMessage());
    }

    @Test
    void testWithoutConstraintViolations() {
        DeserializingMessage message = messageFactory.apply(ConstrainedObject.builder()
                                                                    .aString("foo").aNumber(5).aCustomString("bar")
                                                                    .member(new ConstrainedObjectMember(true))
                                                                    .build());
        subject.interceptHandling(m -> null, null, "test").apply(message);
    }

    @Test
    void testObjectWithoutAnnotations() {
        DeserializingMessage message = messageFactory.apply(new Object());
        subject.interceptHandling(m -> null, null, "test").apply(message);
    }

    @Value
    @Builder
    private static class ConstrainedObject {
        @NotNull String aString;
        @Min(5) long aNumber;
        @NotNull(message = "custom message") String aCustomString;
        @Valid ConstrainedObjectMember member;
        @Singular("aList") List<@NotBlank String> aList;
        @Singular("anotherList") List<@Valid ConstrainedObjectMember> anotherList;
    }

    @Value
    private static class ConstrainedObjectMember {
        @AssertTrue boolean aBoolean;
    }

}