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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;

public class DispatchInterceptorTest {

    private final Object commandHandler = new Object() {
        @HandleCommand
        Object handle(Object command) {
            FluxCapacitor.publishEvent(command);
            return command;
        }
    };

    @Test
    void changeMessageType() {
        TestFixture.create(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(
                                (message, messageType, topic) -> message.withPayload(new DifferentCommand()), COMMAND),
                        commandHandler)
                .whenCommand(new Command(""))
                .expectEvents(new DifferentCommand());
    }

    @Test
    void changeMessageContent() {
        TestFixture.createAsync(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(
                                (message, messageType, topic) -> message.withPayload(new Command("intercepted")), COMMAND),
                        commandHandler)
                .whenCommand(new Command(""))
                .expectEvents(new Command("intercepted"));
    }

    @Test
    void blockMessagePublication() {
        TestFixture.create(
                DefaultFluxCapacitor.builder().addDispatchInterceptor((message, messageType, topic) -> null, COMMAND),
                commandHandler)
                .whenCommand(new Command("whatever"))
                .expectNoEvents().expectNoResult();
    }

    @Test
    void throwException() {
        TestFixture.create(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor((message, messageType, topic) -> {
                            throw new MockException();
                        }, COMMAND), commandHandler)
                .whenCommand(new Command("whatever"))
                .expectNoEvents().expectExceptionalResult(MockException.class);
    }

    @Value
    static class Command {
        String content;
    }

    @Value
    static class DifferentCommand {
    }
}
