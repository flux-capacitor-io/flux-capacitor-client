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
                                .addHandlerInterceptor((f, i) -> m -> "common " + f.apply(m))
                                .addConsumerConfiguration(
                                        ConsumerConfiguration.builder().name("test")
                                                .handlerInterceptor((f, i) -> m -> "consumer-1 " + f.apply(m))
                                                .handlerInterceptor((f, i) -> m -> "consumer-2 " + f.apply(m))
                                                .build()),
                        new Handler())
                .withClock(nowClock)
                .whenCommand(new Command())
                .expectEvents("test")
                .expectResult("consumer-1 consumer-2 common test");
    }

    static class Handler {
        @HandleCommand
        String handle(Command command) {
            String consumerName = Tracker.current().orElseThrow().getConfiguration().getName();
            FluxCapacitor.publishEvent(consumerName);
            return consumerName;
        }
    }

    static class Command {
    }
}
