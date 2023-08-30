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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.jackson.JacksonSerializer;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;

class HandlerInterceptorTest {

    @Test
    void modifyResult() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                (f, i) -> m -> f.apply(m) + "bar", COMMAND), MockCommandHandler.class)
                .whenCommand("foo").expectEvents("foo").expectResult("foobar").expectNoErrors();
    }

    @Test
    void blockCommand() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i) -> m -> null, COMMAND), MockCommandHandler.class)
                .whenCommand("foo").expectNoResult().expectNoEvents().expectNoErrors();
    }

    @Test
    void throwException() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i) -> m -> { throw new MockException(); }, COMMAND), MockCommandHandler.class)
                .whenCommand("foo").expectExceptionalResult(MockException.class).expectNoEvents();
    }

    @Test
    void changePayload() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i) -> m -> f.apply(new DeserializingMessage(
                                m.toMessage().withPayload("foobar"), COMMAND, new JacksonSerializer())),
                        COMMAND), MockCommandHandler.class)
                .whenCommand("foo").expectEvents("foobar").expectResult("foobar").expectNoErrors();
    }

    @Test
    void changePayloadTypeNotSupported() {
        TestFixture.create(DefaultFluxCapacitor.builder().addHandlerInterceptor(
                        (f, i) -> m -> f.apply(new DeserializingMessage(
                                m.toMessage().withPayload(123), COMMAND, new JacksonSerializer())),
                        COMMAND), MockCommandHandler.class)
                .whenCommand("foo").expectExceptionalResult(UnsupportedOperationException.class);
    }

    static class MockCommandHandler {
        @HandleCommand
        String handle(String command) {
            FluxCapacitor.publishEvent(command);
            return command;
        }
    }
}