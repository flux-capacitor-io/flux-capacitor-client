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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.IgnoringErrorHandler;
import io.fluxcapacitor.javaclient.common.exception.TechnicalException;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;
import io.fluxcapacitor.javaclient.tracking.handling.IllegalCommandException;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.fluxcapacitor.common.MessageType.COMMAND;

class GivenWhenThenStreamingTest {

    private final TestFixture
            subject = TestFixture.createAsync(DefaultFluxCapacitor.builder().configureDefaultConsumer(
            COMMAND, config -> config.toBuilder().errorHandler(new IgnoringErrorHandler()).build()),
                                                  new CommandHandler(), new EventHandler(), new AsyncCommandHandler(),
                                                  new ScheduleHandler()).resultTimeout(Duration.ofSeconds(1));

    @Test
    void testExpectCommandsAndIndirectEvents() {
        subject.whenEvent(123).expectNoResult().expectCommands(new YieldsEventAndResult())
                .expectEvents(new YieldsEventAndResult());
    }

    @Test
    void testExpectFunctionalException() {
        subject.whenCommand(new YieldsException()).expectException(MockException.class);
    }

    @Test
    void testExpectTechnicalException() {
        subject.whenCommand(new YieldsRuntimeException()).expectException(TechnicalException.class);
    }

    @Test
    void testAsyncCommandHandling() {
        subject.whenCommand(new YieldsAsyncResult()).expectResult("test");
    }

    @Test
    void testAsyncExceptionHandling() {
        subject.whenCommand(new YieldsAsyncException()).expectException(IllegalCommandException.class);
    }

    @Test
    void testAsyncExceptionHandling2() {
        subject.whenCommand(new YieldsAsyncExceptionSecondHand()).expectException(IllegalCommandException.class);
    }

    @Test
    void testExpectPassiveHandling() {
        subject.givenNoPriorActivity().whenCommand(new PassivelyHandled()).expectException(TimeoutException.class);
    }

    @Test
    void testExpectSchedule() {
        subject.whenCommand(new YieldsSchedule("test")).expectSchedules("test");
    }

    @Test
    void testScheduledCommand() {
        Instant deadline = subject.getClock().instant().plusSeconds(1);
        subject.givenSchedules(new Schedule(new DelayedCommand(), "test", deadline))
                .whenTimeAdvancesTo(deadline).expectOnlyCommands(new DelayedCommand()).expectNoSchedules();
    }

    private static class CommandHandler {
        @HandleCommand
        public String handle(YieldsEventAndResult command) {
            FluxCapacitor.publishEvent(command);
            return "result";
        }

        @HandleCommand
        public void handle(YieldsException command) {
            throw new MockException("expected");
        }

        @HandleCommand
        public void handle(YieldsRuntimeException command) {
            throw new IllegalStateException("expected");
        }

        @HandleCommand(passive = true)
        public String handle(PassivelyHandled command) {
            return "this will be ignored";
        }

        @HandleCommand
        public void handle(YieldsSchedule command) {
            FluxCapacitor.get().scheduler().schedule(command.getSchedule(), Duration.ofSeconds(10));
        }

    }

    private static class EventHandler {
        @HandleEvent
        public void handle(Integer event) throws Exception {
            FluxCapacitor.sendCommand(new YieldsEventAndResult()).get();
        }
    }

    private static class ScheduleHandler {
        @HandleSchedule
        public void handle(DelayedCommand schedule) {
            FluxCapacitor.sendAndForgetCommand(schedule);
        }
    }

    private static class AsyncCommandHandler {

        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        @HandleCommand
        public CompletableFuture<String> handle(YieldsAsyncResult command) {
            CompletableFuture<String> result = new CompletableFuture<>();
            scheduler.schedule(() -> result.complete("test"), 10, TimeUnit.MILLISECONDS);
            return result;
        }

        @HandleCommand
        public CompletableFuture<?> handle(YieldsAsyncException command) {
            CompletableFuture<String> result = new CompletableFuture<>();
            scheduler.schedule(() -> result.completeExceptionally(new IllegalCommandException("test")), 10, TimeUnit.MILLISECONDS);
            return result;
        }

        @HandleCommand
        public CompletableFuture<?> handle(YieldsAsyncExceptionSecondHand command) {
            return FluxCapacitor.sendCommand(new YieldsAsyncException());
        }
    }

    @Value
    private static class YieldsEventAndResult {
    }

    @Value
    private static class YieldsAsyncResult {
    }

    @Value
    private static class YieldsException {
    }

    @Value
    private static class YieldsAsyncException {
    }

    @Value
    private static class YieldsAsyncExceptionSecondHand {
    }

    @Value
    private static class YieldsSchedule {
        Object schedule;
    }

    @Value
    private static class YieldsRuntimeException {
    }

    @Value
    private static class PassivelyHandled {
    }

    @Value
    private static class DelayedCommand {
    }

}
