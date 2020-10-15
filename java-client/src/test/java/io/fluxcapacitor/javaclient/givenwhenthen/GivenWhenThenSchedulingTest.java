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
import io.fluxcapacitor.javaclient.scheduling.Periodic;
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

import static java.time.Instant.now;
import static org.hamcrest.Matchers.isA;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GivenWhenThenSchedulingTest {

    private TestFixture subject = TestFixture.create(new CommandHandler(), new ScheduleHandler());

    @Test
    void testExpectCommandAfterDeadline() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getClock().instant().plusSeconds(10)))
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectCommands(command);
    }

    @Test
    void testExpectCommandAtTimestamp() {
        Object command = "command";
        Instant deadline = subject.getClock().instant().plusSeconds(10);
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test", deadline))
                .whenTimeAdvancesTo(deadline)
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
    void testExpectNoCommandAfterCancel() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getClock().instant().plusSeconds(10)))
                .andGiven(() -> subject.getFluxCapacitor().scheduler().cancelSchedule("test"))
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectNoCommands();
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

    /*
        Test rescheduling
     */

    @Test
    void testNoRescheduleOnVoid() {
        Duration delay = Duration.ofSeconds(10);
        Object payload = new YieldsCommand("whatever");
        subject.givenSchedules(new Schedule(payload, "test", subject.getClock().instant().plus(delay)))
                .whenTimeElapses(delay).expectNoSchedulesLike(isA(YieldsCommand.class));
    }

    @Test
    void testReschedule() {
        Duration delay = Duration.ofSeconds(10);
        YieldsNewSchedule payload = new YieldsNewSchedule(delay.toMillis());
        subject.givenSchedules(new Schedule(payload, "test", subject.getClock().instant().plus(delay)))
                .whenTimeElapses(delay).expectSchedules(payload);
    }

    @Test
    void testNoAutomaticRescheduleBeforeDeadline() {
        subject.givenNoPriorActivity().andThenTimeElapses(Duration.ofMillis(500)).when(() -> {}).expectNoSchedules();
    }

    @Test
    void testAutomaticReschedule() {
        subject.givenNoPriorActivity().andThenTimeElapses(Duration.ofMillis(500))
                .whenTimeElapses(Duration.ofMillis(1000)).expectOnlySchedules(new PeriodicSchedule());
    }

    @Test
    void testAutomaticPeriodicSchedule() {
        subject.givenNoPriorActivity().whenTimeElapses(Duration.ofMillis(1000))
                .expectSchedules(isA(PeriodicSchedule.class));
    }

    @Test
    void testAutomaticPeriodicScheduleWithMethodAnnotation() {
        TestFixture.create(new MethodPeriodicHandler()).givenNoPriorActivity()
                .whenTimeElapses(Duration.ofMillis(1000)).expectSchedules(isA(MethodPeriodicSchedule.class));
    }

    @Test
    void testNonAutomaticPeriodicSchedule() {
        subject.givenNoPriorActivity().whenTimeElapses(Duration.ofMillis(1000))
                .expectNoSchedulesLike(isA(NonAutomaticPeriodicSchedule.class));
    }

    @Test
    void testAlteredPayloadPeriodic() {
        TestFixture.create(new AlteredPayloadPeriodicHandler()).givenNoPriorActivity()
                .whenTimeElapses(Duration.ofMillis(1000)).expectOnlySchedules(new YieldsAlteredSchedule(2));
    }

    @Test
    void testAlteredPayloadNonPeriodic() {
        subject = TestFixture.create(new AlteredPayloadNonPeriodicHandler());
        Instant deadline = subject.getClock().instant().plusSeconds(1);
        subject.givenSchedules(new Schedule(new YieldsAlteredSchedule(), "test", deadline))
                .whenTimeAdvancesTo(deadline).expectOnlySchedules(new YieldsAlteredSchedule(1));
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

        @HandleSchedule
        Duration handle(YieldsNewSchedule schedule) {
            return Duration.ofMillis(schedule.getDelay());
        }

        @HandleSchedule
        void handle(PeriodicSchedule schedule) {
        }

        @HandleSchedule
        void handle(NonAutomaticPeriodicSchedule schedule) {
        }
    }

    static class MethodPeriodicHandler {
        @HandleSchedule
        @Periodic(1000)
        void handle(MethodPeriodicSchedule schedule) {
        }
    }

    static class AlteredPayloadPeriodicHandler {
        @HandleSchedule
        @Periodic(1000)
        YieldsAlteredSchedule handle(YieldsAlteredSchedule schedule) {
            return new YieldsAlteredSchedule(schedule.getSequence() + 1);
        }
    }

    static class AlteredPayloadNonPeriodicHandler {
        @HandleSchedule
        YieldsAlteredSchedule handle(YieldsAlteredSchedule schedule) {
            return new YieldsAlteredSchedule(schedule.getSequence() + 1);
        }
    }

    @AllArgsConstructor
    @Value
    static class YieldsSchedule {
        Schedule schedule;

        public YieldsSchedule() {
            this(new Schedule("schedule", UUID.randomUUID().toString(), now().plusSeconds(10)));
        }
    }

    @Value
    static class YieldsCommand {
        Object command;
    }

    @Value
    static class YieldsNewSchedule {
        long delay;
    }

    @Value
    @AllArgsConstructor
    static class YieldsAlteredSchedule {
        int sequence;

        public YieldsAlteredSchedule() {
            this(0);
        }
    }

    @Value
    @Periodic(1000)
    static class PeriodicSchedule {
    }

    @Value
    static class MethodPeriodicSchedule {
    }

    @Value
    @Periodic(value = 1000, autoStart = false)
    static class NonAutomaticPeriodicSchedule {
    }

}
