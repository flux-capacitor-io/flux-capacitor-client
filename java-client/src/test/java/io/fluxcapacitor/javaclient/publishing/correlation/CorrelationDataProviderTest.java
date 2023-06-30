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

package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

class CorrelationDataProviderTest {
    private final CorrelationDataProvider testProvider = msg -> {
        Map<String, String> result = new HashMap<>(Map.of("foo", "bar"));
        if (msg != null) {
            result.put("msgId", msg.getMessageId());
        }
        return result;
    };
    private final DefaultCorrelationDataProvider defaultProvider = DefaultCorrelationDataProvider.INSTANCE;

    @Test
    void provideCommandAndEventMetadata() {
        var command = new Message("bla");
        TestFixture.create(DefaultFluxCapacitor.builder().replaceCorrelationDataProvider(
                defaultProvider -> testProvider), new CommandHandler())
                .whenExecuting(fc -> fc.commandGateway().sendAndForget(command))
                .expectCommands(command.addMetadata("foo", "bar"))
                .expectEvents(command.addMetadata("foo", "bar", "msgId", command.getMessageId()));
    }

    @Test
    void extendDefaultProvider() {
        var command = new Message("bla");
        TestFixture.create(DefaultFluxCapacitor.builder().replaceCorrelationDataProvider(
                defaultProvider -> defaultProvider.andThen(testProvider)), new CommandHandler())
                .whenExecuting(fc -> fc.commandGateway().sendAndForget(command))
                .expectCommands(command.addMetadata("foo", "bar"))
                .expectCommands((Predicate<Message>) c -> c.getMetadata().containsKey(defaultProvider.getClientIdKey()))
                .expectEvents(command.addMetadata("foo", "bar", "msgId", command.getMessageId(),
                                                  defaultProvider.getCorrelationIdKey(), command.getMessageId()));
    }

    private static class CommandHandler {
        @HandleCommand
        void handle(Object command) {
            FluxCapacitor.publishEvent(command);
        }
    }

}