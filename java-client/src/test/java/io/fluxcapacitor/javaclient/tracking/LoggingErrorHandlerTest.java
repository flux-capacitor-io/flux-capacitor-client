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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.ApplyEvent;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class LoggingErrorHandlerTest {

    private final ThrowingHandler handler = new ThrowingHandler();
    private final TestFixture testFixture = TestFixture.createAsync(handler);

    @Test
    void loggedHandlerErrorHasTraceId() {
        testFixture.whenCommand("error")
                .expectThat(fc -> verify(fc.client().getGatewayClient(MessageType.ERROR)).send(
                        any(), ArgumentMatchers.<SerializedMessage>argThat(
                                message -> message.getMetadata().containsKey("$consumer")
                                           && message.getMetadata().containsKey("$correlationId")
                                           && message.getMetadata().containsKey("$traceId"))));
    }

    private static class ThrowingHandler {
        @HandleCommand
        private void handle(String command) {
            FluxCapacitor.loadAggregate("test", Aggregate.class).apply(command);
        }
    }

    private static class Aggregate {
        @ApplyEvent
        static Aggregate create(String event) {
            throw new IllegalArgumentException();
        }
    }

}