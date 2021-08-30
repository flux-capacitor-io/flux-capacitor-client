/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.tracking.handling.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.junit.jupiter.api.Test;

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
        assertEquals("aNumberWithGroupConstraint must be greater than or equal to 5", e.getMessage());
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