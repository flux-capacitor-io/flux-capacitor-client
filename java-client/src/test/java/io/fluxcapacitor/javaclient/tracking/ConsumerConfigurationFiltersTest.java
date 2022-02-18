package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
public class ConsumerConfigurationFiltersTest {
    private final Clock nowClock = Clock.fixed(Instant.parse("2022-01-01T00:00:00.000Z"), ZoneId.systemDefault());
    private final Long nowIndex = 107544261427200000L;

    @Test
    void nonExclusiveConsumerLetsHandlerThrough() {
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder().messageType(COMMAND)
                                                                          .name("nonExclusive")
                                                                          .prependApplicationName(false)
                                                                          .exclusive(false).build())
                                        .addConsumerConfiguration(ConsumerConfiguration.builder().messageType(COMMAND)
                                                                          .name("exclusive")
                                                                          .prependApplicationName(false).build())
                                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().name("default").build()),
                                new Handler())
                .givenNoPriorActivity()
                .whenCommand(new Command())
                .expectOnlyEvents("nonExclusive", "exclusive");
    }

    @Test
    void passiveConsumerReturnsNothing() {
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder().messageType(COMMAND)
                                                                          .name("nonExclusivePassive")
                                                                          .prependApplicationName(false)
                                                                          .exclusive(false).passive(true).build())
                                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().name("default").build()),
                                new Handler())
                .givenNoPriorActivity()
                .whenCommand(new Command())
                .expectOnlyEvents("nonExclusivePassive", "default")
                .expectResult("default");
    }

    @Test
    void exceptionWhenHandlerHasNoConsumer() {
        assertThrows(TrackingException.class, () ->
                TestFixture.createAsync(
                        DefaultFluxCapacitor.builder().configureDefaultConsumer(COMMAND, c -> c.toBuilder()
                                .handlerFilter(h -> !h.getClass().equals(Handler.class)).build()),
                        new Handler()));
    }

    @Test
    void noExceptionWhenHandlerHasOnlyNonExclusiveConsumer() {
        assertDoesNotThrow(() -> TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                                                 .configureDefaultConsumer(COMMAND, c -> c.toBuilder()
                                                                         .exclusive(false).build()),
                                                         new Handler()));
    }

    @Test
    void dontProcessMessageWhenMaxIndexIsReached() {
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder().messageType(COMMAND)
                                                                          .name("minIndex")
                                                                          .prependApplicationName(false)
                                                                          .exclusive(false).minIndex(nowIndex).build())
                                        .addConsumerConfiguration(ConsumerConfiguration.builder().messageType(COMMAND)
                                                                          .name("maxIndex")
                                                                          .prependApplicationName(false)
                                                                          .maxIndexExclusive(nowIndex).build())
                                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().name("default").build()),
                                new Handler())
                .withClock(nowClock)
                .givenNoPriorActivity()
                .whenCommand(new Command())
                .expectEvents("minIndex")
                .expectResult("minIndex");
    }

    static class Handler {
        @HandleCommand
        String handle(Command command) {
            String consumerName = Tracker.current().get().getConfiguration().getName();
            FluxCapacitor.publishEvent(consumerName);
            return consumerName;
        }
    }

    static class Command {
    }
}
