package io.fluxcapacitor.javaclient.tracking.handling.validation;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Jsr380ValidatorTest {

    private final Jsr380Validator subject = Jsr380Validator.createDefault();

    @Test
    void testObjectWithoutAnnotations() {
        subject.assertValid(new Object());
    }

    @Test
    void testValidObject() {
        Object object = ConstrainedObject.builder()
                .aString("foo").aNumber(5).aCustomString("bar")
                .member(new ConstrainedObjectMember(true))
                .build();
        subject.assertValid(object);
    }

    @Test
    void testInvalidObject() {
        Object object = ConstrainedObject.builder()
                .aString(null).aNumber(3).aCustomString(null)
                .member(new ConstrainedObjectMember(false))
                .aList("")
                .anotherList(new ConstrainedObjectMember(false))
                .build();
        ValidationException e = assertThrows(ValidationException.class, () -> subject.assertValid(object));

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
    void testGroupConstraints() {
        Object object = ConstrainedObject.builder()
                .aString("foo").aNumber(5).aCustomString("bar")
                .member(new ConstrainedObjectMember(true))
                .aNumberWithGroupConstraint(2)
                .build();
        ValidationException e =
                assertThrows(ValidationException.class, () -> subject.assertValid(object, Group.class));
        assertEquals(1, e.getViolations().size());
        assertEquals("aSpecialNumber must be greater than or equal to 5", e.getMessage());
    }

    @Value
    @Builder
    private static class ConstrainedObject {
        @NotNull String aString;
        @Min(5) long aNumber;
        @NotNull(message = "custom message") String aCustomString;
        @Valid
        ConstrainedObjectMember member;
        @Singular("aList")
        List<@NotBlank String> aList;
        @Singular("anotherList")
        List<@Valid ConstrainedObjectMember> anotherList;
        @Min(value = 5, groups = Group.class) long aNumberWithGroupConstraint;
    }

    @Value
    private static class ConstrainedObjectMember {
        @AssertTrue boolean aBoolean;
    }

    private interface Group {
    }

}