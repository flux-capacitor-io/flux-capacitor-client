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
    void testGivenExpiredSchedule() {
        Duration delay = Duration.ofMinutes(10);
        YieldsNewSchedule schedule = new YieldsNewSchedule(delay.toMillis());
        subject.givenExpiredSchedules(schedule)
                .whenTimeElapses(delay)
                .expectNewSchedules(schedule);
    }

    @Test
    void testGivenScheduleWithTimeInPastExecuteBeforeTest() {
        Object command = "command";
        Instant deadline = subject.getClock().instant().minusSeconds(10);
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test", deadline))
                .when(fc -> {})
                .expectNoCommands();
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
                .given(fc -> fc.scheduler().cancelSchedule("test"))
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectNoCommands();
    }

    /*
        Test when expires
     */

    @Test
    void testWhenExpires() {
        Object command = "command";
        subject.whenScheduleExpires(new YieldsCommand(command)).expectCommands(command);
    }

    /*
        Test expect
     */

    @Test
    void testExpectSchedule() {
        YieldsSchedule command = new YieldsSchedule();
        subject.whenCommand(command)
                .expectOnlyNewSchedules(command.getSchedule())
                .expectSchedules(command.getSchedule(), PeriodicSchedule.class);
    }

    @Test
    void testExpectScheduleAnyTime() {
        YieldsSchedule command = new YieldsSchedule();
        subject.whenCommand(command).expectOnlyNewSchedules(command.getSchedule());
    }

    @Test
    void testExpectNoScheduleLike() {
        subject.whenCommand(new YieldsSchedule()).expectNoNewSchedulesLike("anotherPayload");
    }

    @Test
    void testExpectScheduleWithoutAnySchedules() {
        assertThrows(GivenWhenThenAssertionError.class,
                     () -> subject.whenCommand("command").expectOnlyNewSchedules("schedule"));
    }

    @Test
    void testExpectSchedulePayloadMismatch() {
        assertThrows(GivenWhenThenAssertionError.class,
                     () -> subject.whenCommand(new YieldsSchedule()).expectOnlyNewSchedules("otherPayload"));
    }

    /*
        Test rescheduling
     */

    @Test
    void testNoRescheduleOnVoid() {
        Duration delay = Duration.ofSeconds(10);
        Object payload = new YieldsCommand("whatever");
        subject.givenSchedules(new Schedule(payload, "test", subject.getClock().instant().plus(delay)))
                .whenTimeElapses(delay)
                .expectNoNewSchedulesLike(YieldsCommand.class)
                .expectNoSchedulesLike(YieldsCommand.class);
    }

    @Test
    void testReschedule() {
        Duration delay = Duration.ofSeconds(10);
        YieldsNewSchedule payload = new YieldsNewSchedule(delay.toMillis());
        subject.givenSchedules(new Schedule(payload, "test", subject.getClock().instant().plus(delay)))
                .whenTimeElapses(delay).expectNewSchedules(payload);
    }

    @Test
    void testScheduleOverride() {
        Duration delay = Duration.ofSeconds(10);
        Object expected = new YieldsCommand("original");
        Object notExpected = new YieldsCommand("override");
        subject.givenSchedules(new Schedule(expected, "test", subject.getClock().instant().plus(delay)))
                .givenSchedules(new Schedule(notExpected, "test", subject.getClock().instant().plus(delay).minusSeconds(1)))
                .whenTimeElapses(delay).expectOnlyCommands("override");
    }

    @Test
    void testNoOverrideWithAbsentCheck() {
        Duration delay = Duration.ofSeconds(10);
        Object expected = new YieldsCommand("original");
        Object notExpected = new YieldsCommand("override");
        subject.givenSchedules(new Schedule(expected, "test", subject.getClock().instant().plus(delay)))
                .givenScheduleIfAbsent(new Schedule(notExpected, "test", subject.getClock().instant().plus(delay).minusSeconds(1)))
                .whenTimeElapses(delay).expectOnlyCommands("original");
    }

    @Test
    void testNoAutomaticRescheduleBeforeDeadline() {
        subject.givenNoPriorActivity().givenTimeElapses(Duration.ofMillis(500)).when(fc -> {}).expectNoNewSchedules();
    }

    @Test
    void testAutomaticReschedule() {
        subject.givenNoPriorActivity().givenTimeElapses(Duration.ofMillis(500))
                .whenTimeElapses(Duration.ofMillis(1000)).expectOnlyNewSchedules(new PeriodicSchedule());
    }

    @Test
    void testAutomaticPeriodicSchedule() {
        subject.givenNoPriorActivity().whenTimeElapses(Duration.ofMillis(1000))
                .expectNewSchedules(PeriodicSchedule.class);
    }

    @Test
    void testAutomaticPeriodicScheduleWithMethodAnnotation() {
        TestFixture.create(new MethodPeriodicHandler()).givenNoPriorActivity()
                .whenTimeElapses(Duration.ofMillis(1000)).expectNewSchedules(MethodPeriodicSchedule.class);
    }

    @Test
    void testNonAutomaticPeriodicSchedule() {
        subject.givenNoPriorActivity().whenTimeElapses(Duration.ofMillis(1000))
                .expectNoNewSchedulesLike(NonAutomaticPeriodicSchedule.class);
    }

    @Test
    void testAlteredPayloadPeriodic() {
        TestFixture.create(new AlteredPayloadPeriodicHandler()).givenNoPriorActivity()
                .whenTimeElapses(Duration.ofMillis(1000)).expectOnlyNewSchedules(new YieldsAlteredSchedule(2));
    }

    @Test
    void testAlteredPayloadNonPeriodic() {
        subject = TestFixture.create(new AlteredPayloadNonPeriodicHandler());
        Instant deadline = subject.getClock().instant().plusSeconds(1);
        subject.givenSchedules(new Schedule(new YieldsAlteredSchedule(), "test", deadline))
                .whenTimeAdvancesTo(deadline).expectOnlyNewSchedules(new YieldsAlteredSchedule(1));
    }

    @Test
    void testAlteredPayloadNonPeriodicReturningSchedule() {
        subject = TestFixture.create(new AlteredPayloadNonPeriodicHandlerReturningSchedule());
        Instant deadline = subject.getClock().instant().plusSeconds(1);
        subject.givenSchedules(new Schedule(new YieldsAlteredSchedule(), "test", deadline))
                .whenTimeAdvancesTo(deadline).expectOnlyNewSchedules(new YieldsAlteredSchedule(1));
    }

    @Test
    void testInterfacePeriodicHandler() {
        TestFixture.create(new InterfacePeriodicHandler())
                .givenNoPriorActivity().whenTimeElapses(Duration.ofMillis(1000))
                .expectNewSchedules(PeriodicScheduleFromInterface.class);
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

    static class InterfacePeriodicHandler {
        @HandleSchedule
        void handle(PeriodicScheduleFromInterface schedule) {
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
        YieldsAlteredSchedule handle(YieldsAlteredSchedule payload) {
            return new YieldsAlteredSchedule(payload.getSequence() + 1);
        }
    }

    static class AlteredPayloadNonPeriodicHandlerReturningSchedule {
        @HandleSchedule
        Schedule handle(YieldsAlteredSchedule payload, Schedule schedule) {
            return schedule.withPayload(new YieldsAlteredSchedule(payload.getSequence() + 1))
                    .reschedule(Duration.ofSeconds(1));
        }
    }

    @AllArgsConstructor
    @Value
    class YieldsSchedule {
        Schedule schedule;

        public YieldsSchedule() {
            this(new Schedule("schedule", UUID.randomUUID().toString(),
                              subject.getClock().instant().plusSeconds(10)));
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

    @Value
    static class PeriodicScheduleFromInterface implements PeriodicInterface {
    }

    @Periodic(1000)
    interface PeriodicInterface {
    }

}
