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

import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Test;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import static io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils.isValid;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilsTest {

    @Test
    void testValidation() {
        assertTrue(isValid(new Foo("bar")));
        assertFalse(isValid(new Foo("")));
        assertTrue(ValidationUtils.checkValidity(new Foo("")).isPresent());
        assertThrows(ValidationException.class, () -> ValidationUtils.assertValid(new Foo("")));
    }

    @Test
    void testValidateWith() {
        assertTrue(isValid(ValidateWithExample.builder().foo(new Foo("bar")).stringInGroup1("bla").build()));
        assertFalse(isValid(ValidateWithExample.builder().foo(new Foo("bar")).stringInGroup1("").build()));
        assertTrue(isValid(ValidateWithExample.builder().foo(new Foo("")).stringInGroup1("bla").build()));
    }

    @Value
    public static class Foo {
        @NotBlank String bar;
    }

    @Value
    @Builder
    @ValidateWith(Group1.class)
    public static class ValidateWithExample {
        @Valid Foo foo;
        @NotBlank(groups = Group1.class) String stringInGroup1;
    }

    public interface Group1 {}

}