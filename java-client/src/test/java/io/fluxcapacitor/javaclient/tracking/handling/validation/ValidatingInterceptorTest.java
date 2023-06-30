/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.junit.jupiter.api.Test;

class ValidatingInterceptorTest {
    private final TestFixture testFixture = TestFixture.create(new MockHandler());

    @Test
    void testWithConstraintViolations() {
        testFixture.whenCommand(new BasicCommand(null)).expectExceptionalResult(ValidationException.class);
    }

    @Test
    void testWithoutConstraintViolations() {
        testFixture.whenCommand(new BasicCommand("foo")).expectSuccessfulResult();
    }

    @Test
    void testValidateWith_invalid() {
        testFixture.whenCommand(new CommandWithGroupValidation("foo", null))
                .expectExceptionalResult(ValidationException.class);
    }

    @Test
    void testValidateWith_valid() {
        testFixture.whenCommand(new CommandWithGroupValidation("foo", "bar"))
                .expectSuccessfulResult();
    }

    @Test
    void testValidateWith_valid2() {
        testFixture.whenCommand(new CommandWithGroupValidation(null, "bar"))
                .expectSuccessfulResult();
    }

    @Value
    private static class BasicCommand {
        @NotNull String aString;
        @NotNull(groups = GroupA.class) String groupAString = null;
    }

    @Value
    @ValidateWith(GroupA.class)
    private static class CommandWithGroupValidation {
        @NotNull String aString;
        @NotNull(groups = GroupA.class) String groupAString;
    }

    private interface GroupA {
    }

    private static class MockHandler {
        @HandleCommand
        void handle(Object command) {
        }
    }

}