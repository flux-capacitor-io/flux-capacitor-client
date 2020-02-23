/*
 * Copyright (c) 2016-2018 Flux Capacitor.
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
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.test.GivenWhenThenAssertionError;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GivenWhenThenSchedulingTest {

    private final TestFixture subject = TestFixture.create(new CommandHandler(), new ScheduleHandler());

    @Test
    void testExpectCommandAfterDeadline() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getClock().instant().plusSeconds(10)))
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectCommands(command);
    }

    @Test
    void testExpectNoCommandBeforeDeadline() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getClock().instant().plusSeconds(10)))
                .whenTimeElapses(Duration.ofSeconds(10).minusMillis(1))
                .expectNoCommands();
    }

    @Test
    void testExpectCommandExactTime() {
        Object command = "command";
        Instant deadline = subject.getClock().instant().plusSeconds(10);
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test", deadline))
                .whenTimeAdvancesTo(deadline)
                .expectCommands(command);
    }
    
    /*
        Test expect
     */

    @Test
    void testExpectSchedule() {
        YieldsSchedule command = new YieldsSchedule();
        subject.whenCommand(command).expectOnlySchedules(command.getSchedule());
    }

    @Test
    void testExpectScheduleAnyTime() {
        YieldsSchedule command = new YieldsSchedule();
        subject.whenCommand(command).expectOnlySchedules(command.getSchedule());
    }

    @Test
    void testExpectNoScheduleLike() {
        subject.whenCommand(new YieldsSchedule()).expectNoSchedulesLike("anotherPayload");
    }

    @Test
    void testExpectScheduleWithoutAnySchedules() {
        assertThrows(GivenWhenThenAssertionError.class,
                     () -> subject.whenCommand("command").expectOnlySchedules("schedule"));
    }

    @Test
    void testExpectSchedulePayloadMismatch() {
        assertThrows(GivenWhenThenAssertionError.class,
                     () -> subject.whenCommand(new YieldsSchedule()).expectOnlySchedules("otherPayload"));
    }

    static class CommandHandler {
        @HandleCommand
        void handle(YieldsSchedule command) {
            FluxCapacitor.get().scheduler().schedule(command.getSchedule());
        }
    }

    static class ScheduleHandler {
        @HandleSchedule
        void handle(YieldsCommand schedule) {
            FluxCapacitor.get().commandGateway().sendAndForget(schedule.getCommand());
        }
    }

    @AllArgsConstructor
    @Value
    static class YieldsSchedule {
        Schedule schedule;

        public YieldsSchedule() {
            this(new Schedule("schedule", UUID.randomUUID().toString(), Instant.now().plusSeconds(10)));
        }
    }

    @Value
    static class YieldsCommand {
        Object command;
    }

}
