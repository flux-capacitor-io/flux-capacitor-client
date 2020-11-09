/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.test.AbstractTestFixture;
import io.fluxcapacitor.javaclient.test.streaming.StreamingTestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static io.fluxcapacitor.javaclient.FluxCapacitor.loadAggregate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

@Slf4j
class EventSourcingIntegrationTest {
    private final AbstractTestFixture testFixture = StreamingTestFixture.create(new CommandHandler(), new EventHandler());

    @Test
    void testHandleBatch() {
        testFixture.givenCommands(new AggregateCommand("test", "0"), new AggregateCommand("test", "1"),
                                  new AggregateCommand("test", "2"))
                .whenCommand(new AggregateCommand("test", "3"))
                .expectOnlyEvents(new AggregateCommand("test", "3"))
                .expectOnlyCommands(new SecondOrderCommand());

        assertEquals(4, testFixture.getFluxCapacitor().eventStore().getDomainEvents("test").count());

        verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), atMost(3))
                .storeEvents(eq("test"), anyString(), anyLong(), anyList());
    }

    static class CommandHandler {
        @HandleCommand
        void handle(AggregateCommand command) {
            loadAggregate(command.id, Aggregate.class).apply(command);
        }
    }

    @Slf4j
    static class EventHandler {
        @HandleEvent
        void handle(AggregateCommand event) {
            FluxCapacitor.sendAndForgetCommand(new SecondOrderCommand());
        }
    }

    @EventSourced
    @Value
    @Builder(toBuilder = true)
    static class Aggregate {
        String id;
        String value;

        @ApplyEvent
        static Aggregate create(AggregateCommand event) {
            return Aggregate.builder().id(event.id).value(event.value).build();
        }

        @ApplyEvent
        Aggregate update(AggregateCommand event) {
            return toBuilder().value(event.value).build();
        }
    }

    @Value
    static class AggregateCommand {
        @RoutingKey
        String id;
        String value;
    }

    @Value
    static class SecondOrderCommand {
    }
}