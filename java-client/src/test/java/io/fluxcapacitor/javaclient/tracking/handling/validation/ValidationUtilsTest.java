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

import javax.validation.constraints.NotBlank;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationUtilsTest {

    @Test
    void testValidation() {
        assertTrue(ValidationUtils.isValid(new Foo("bar")));
        assertFalse(ValidationUtils.isValid(new Foo("")));
        assertTrue(ValidationUtils.checkValidity(new Foo("")).isPresent());
        assertThrows(ValidationException.class, () -> ValidationUtils.assertValid(new Foo("")));
    }

    @Value
    public static class Foo {
        @NotBlank String bar;
    }

}