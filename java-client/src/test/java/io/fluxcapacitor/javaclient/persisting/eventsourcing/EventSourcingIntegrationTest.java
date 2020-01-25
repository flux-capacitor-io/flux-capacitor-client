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

import java.util.List;

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
                                  new AggregateCommand("test", "2")).whenCommand(new AggregateCommand("test", "3"))
                .expectOnlyEvents(new AggregateCommand("test", "3"))
                .expectOnlyCommands(new SecondOrderCommand());

        assertEquals(4, testFixture.getFluxCapacitor().eventStore().getDomainEvents("test").count());
        
        verify(testFixture.getFluxCapacitor().client().getEventStoreClient(), atMost(3))
                .storeEvents(eq("test"), anyString(), anyLong(), anyList());
    }

    static class CommandHandler {
        @HandleCommand
        void handle(List<AggregateCommand> commands) {
            commands.forEach(command -> loadAggregate(command.id, Aggregate.class).apply(command));
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