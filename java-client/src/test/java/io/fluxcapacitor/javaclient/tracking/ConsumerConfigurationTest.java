package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
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
public class ConsumerConfigurationTest {
    private final Clock nowClock = Clock.fixed(Instant.parse("2022-01-01T00:00:00.000Z"), ZoneId.systemDefault());

    @Test
    void nonExclusiveConsumerLetsHandlerThrough() {
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("nonExclusive")
                                                                          .passive(true)
                                                                          .exclusive(false).build(),
                                                                  MessageType.COMMAND)
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("exclusive")
                                                                          .build(), MessageType.COMMAND)
                                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().name("default").build()),
                                new Handler())

                .whenCommand(new Command())
                .expectOnlyEvents("nonExclusive", "exclusive");
    }

    @Test
    void orderOfExclusiveVsNonExclusiveDoesntMatter() {
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("exclusive1")
                                                                          .build())
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("exclusive2")
                                                                          .build())
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("nonExclusive")
                                                                          .passive(true)
                                                                          .exclusive(false).build())
                                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().name("default").build()),
                                new Handler())

                .whenCommand(new Command())
                .expectOnlyEvents("nonExclusive", "exclusive1");
    }

    @Test
    void passiveConsumerReturnsNothing() {
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("nonExclusivePassive")
                                                                          .exclusive(false).passive(true).build(),
                                                                  MessageType.COMMAND)
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("default").build(), MessageType.COMMAND),
                                new Handler())

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
        Long nowIndex = 107544261427200000L;
        TestFixture.createAsync(DefaultFluxCapacitor.builder()
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("minIndex")
                                                                          .exclusive(false).minIndex(nowIndex).build(),
                                                                  MessageType.COMMAND)
                                        .addConsumerConfiguration(ConsumerConfiguration.builder()
                                                                          .name("maxIndex")
                                                                          .maxIndexExclusive(nowIndex).build(),
                                                                  MessageType.COMMAND)
                                        .configureDefaultConsumer(COMMAND, c -> c.toBuilder().name("default").build()),
                                new Handler())
                .withClock(nowClock)

                .whenCommand(new Command())
                .expectEvents("minIndex")
                .expectResult("minIndex");
    }

    @Test
    void interceptorInConsumerTest() {
        TestFixture.createAsync(
                        DefaultFluxCapacitor.builder()
                                .addHandlerInterceptor((f, i, c) -> m -> "first " + f.apply(m))
                                .addConsumerConfiguration(
                                        ConsumerConfiguration.builder().name("test")
                                                .handlerInterceptor((f, i, c) -> m -> "second " + f.apply(m))
                                                .handlerInterceptor((f, i, c) -> m -> "third " + f.apply(m))
                                                .build()),
                        new Handler())
                .withClock(nowClock)
                .whenCommand(new Command())
                .expectEvents("test")
                .expectResult("first second third test");
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
