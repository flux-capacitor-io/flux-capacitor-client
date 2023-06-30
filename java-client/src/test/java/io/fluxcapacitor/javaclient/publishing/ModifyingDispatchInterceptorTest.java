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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.common.MessageType.COMMAND;

public class ModifyingDispatchInterceptorTest {

    @Test
    void testThatInterceptorChangesMessageTypeSuccessfully() {
        TestFixture.create(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(new ChangeTypeInterceptor(), COMMAND),
                        new CommandHandler())
                
                .whenCommand(new Command(""))
                .expectEvents(new DifferentCommand());
    }

    @Test
    void testThatInterceptorChangesMessageContentSuccessfully() {
        TestFixture.create(
                        DefaultFluxCapacitor.builder().addDispatchInterceptor(new ChangeContentInterceptor(), COMMAND),
                        new CommandHandler())
                
                .whenCommand(new Command(""))
                .expectEvents(new Command("intercepted"));
    }


    public static class ChangeTypeInterceptor implements DispatchInterceptor {
        @Override
        public Message interceptDispatch(Message message, MessageType messageType) {
            return message.withPayload(new DifferentCommand());
        }
    }

    public static class ChangeContentInterceptor implements DispatchInterceptor {
        @Override
        public Message interceptDispatch(Message message, MessageType messageType) {
            return message.withPayload(new Command("intercepted"));
        }
    }

    private static class CommandHandler {
        @HandleCommand
        public void handle(Object command) {
            FluxCapacitor.publishEvent(command);
        }
    }

    @Value
    public static class Command {
        String content;
    }

    @Value
    public static class DifferentCommand {
    }
}
