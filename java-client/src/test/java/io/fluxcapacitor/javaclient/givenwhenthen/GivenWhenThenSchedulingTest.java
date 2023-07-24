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

package io.fluxcapacitor.javaclient.givenwhenthen;

import io.fluxcapacitor.common.TestUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.scheduling.Periodic;
import io.fluxcapacitor.javaclient.scheduling.Schedule;
import io.fluxcapacitor.javaclient.test.GivenWhenThenAssertionError;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleCommand;
import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.fluxcapacitor.javaclient.FluxCapacitor.publishEvent;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GivenWhenThenSchedulingTest {

    private static final String atStartOfDay = "0 0 * * *";
    private TestFixture subject = TestFixture.create(new CommandHandler(), new ScheduleHandler());

    @Test
    void testExpectCommandAfterDeadline() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getCurrentTime().plusSeconds(10)))
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectCommands(command);
    }

    @Test
    void testExpectCommandAtTimestamp() {
        Object command = "command";
        Instant deadline = subject.getCurrentTime().plusSeconds(10);
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
        Instant deadline = subject.getCurrentTime().minusSeconds(10);
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test", deadline))
                .whenExecuting(fc -> {
                })
                .expectNoCommands();
    }

    @Test
    void testExpectNoCommandBeforeDeadline() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getCurrentTime().plusSeconds(10)))
                .whenTimeElapses(Duration.ofSeconds(10).minusMillis(1))
                .expectNoCommands();
    }

    @Test
    void testExpectNoCommandAfterCancel() {
        Object command = "command";
        subject.givenSchedules(new Schedule(new YieldsCommand(command), "test",
                                            subject.getCurrentTime().plusSeconds(10)))
                .given(fc -> fc.scheduler().cancelSchedule("test"))
                .whenTimeElapses(Duration.ofSeconds(10))
                .expectNoCommands();
    }

    @Nested
    class CronSchedules {
        private final Instant start = Instant.parse("2023-07-01T12:10:00Z");
        private final Instant afterOneHour = start.truncatedTo(ChronoUnit.HOURS).plus(Duration.ofHours(1));

        private final TestFixture testFixture = TestFixture.create().atFixedTime(start)
                .registerHandlers(new Object() {
                    @HandleSchedule
                    @Periodic(cron = "0 * * * *")
                    void handleSchedule(CronSchedule schedule, Schedule message) {
                        publishEvent(message.getDeadline());
                    }
                });

        @Test
        void testPeriodicCronSchedule() {
            testFixture.whenTimeAdvancesTo(afterOneHour).expectOnlyEvents(afterOneHour);
        }

        @Test
        void testSecondPeriodicCronSchedule() {
            testFixture.givenTimeAdvancedTo(afterOneHour)
                    .whenTimeElapses(Duration.ofHours(1))
                    .expectOnlyEvents(afterOneHour.plus(Duration.ofHours(1)));
        }

        @Test
        void testPeriodicCronScheduleViaProperty() {
            TestUtils.runWithSystemProperties(() -> {
                TestFixture.create().atFixedTime(start)
                        .registerHandlers(new Object() {
                            @HandleSchedule
                            @Periodic(cron = "${cronSchedule:-}")
                            void handleSchedule(CronSchedule schedule, Schedule message) {
                                publishEvent(message.getDeadline());
                            }
                        }).whenTimeAdvancesTo(afterOneHour).expectOnlyEvents(afterOneHour);
            }, "cronSchedule", "0 * * * *");
        }

        @Test
        void testNoScheduleBeforeDeadline() {
            testFixture.whenTimeAdvancesTo(afterOneHour.minus(Duration.ofMinutes(1))).expectNoEvents();
        }

        @Test
        void disablePeriodicUsingSpecialExpression() {
            TestFixture.create().atFixedTime(start)
                    .registerHandlers(new Object() {
                        @HandleSchedule
                        @Periodic(cron = Periodic.DISABLED)
                        void handleSchedule(CronSchedule schedule, Schedule message) {
                            publishEvent(message.getDeadline());
                        }
                    }).whenTimeElapses(Duration.ofMinutes(10))
                    .expectNoEvents().expectNoSchedules();
        }

        @Test
        void useCronWithTimeZone() {
            TestFixture.create().atFixedTime(Instant.parse("2023-07-01T12:00:00+02:00"))
                    .registerHandlers(new Object() {
                        @HandleSchedule
                        @Periodic(cron = atStartOfDay, timeZone = "Europe/Amsterdam")
                        void handleSchedule(CronSchedule schedule, Schedule message) {
                            publishEvent(message.getDeadline());
                        }
                    }).whenTimeElapses(Duration.ofDays(1))
                    .expectOnlyEvents(Instant.parse("2023-07-02T00:00:00+02:00"));
        }

        @Test
        void disablePeriodicUsingSpecialExpression_viaMissingProperty() {
            TestFixture.create().atFixedTime(start)
                    .registerHandlers(new Object() {
                        @HandleSchedule
                        @Periodic(cron = "${someMissingProperty:-}")
                        void handleSchedule(CronSchedule schedule, Schedule message) {
                            publishEvent(message.getDeadline());
                        }
                    }).whenTimeElapses(Duration.ofMinutes(10))
                    .expectNoEvents().expectNoSchedules();
        }
    }

    @Nested
    class SchedulingErrorTests {
        @Test
        void stopAfterError() {
            TestFixture.create(new Object() {
                        @HandleSchedule
                        @Periodic(continueOnError = false, delay = 60, timeUnit = TimeUnit.MINUTES)
                        void handleSchedule(Object schedule) {
                            throw new MockException();
                        }
                    })
                    .whenTimeElapses(Duration.ofMinutes(10))
                    .expectNoSchedules();
        }

        @Test
        void continueAfterError() {
            TestFixture.create(new Object() {
                        private int count = 0;
                        @HandleSchedule
                        @Periodic(delay = 60, timeUnit = TimeUnit.MINUTES)
                        void handleSchedule(Object schedule) {
                            if (++count == 1) {
                                throw new MockException();
                            } else {
                                FluxCapacitor.publishEvent("success");
                            }
                        }
                    })
                    .whenTimeElapses(Duration.ofMinutes(10))
                    .expectSchedules(Object.class).expectNoEvents();
        }

        @Test
        void otherDelayAfterError() {
            TestFixture.create(new Object() {
                        private int count = 0;

                        @HandleSchedule
                        @Periodic(delayAfterError = 10, delay = 60, timeUnit = TimeUnit.MINUTES)
                        void handleSchedule(Object schedule) {
                            if (++count == 1) {
                                throw new MockException();
                            } else {
                                FluxCapacitor.publishEvent("success");
                            }
                        }
                    })
                    .whenTimeElapses(Duration.ofMinutes(10))
                    .expectEvents("success");
        }
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
        subject.givenSchedules(new Schedule(payload, "test", subject.getCurrentTime().plus(delay)))
                .whenTimeElapses(delay)
                .expectNoNewSchedulesLike(YieldsCommand.class)
                .expectNoSchedulesLike(YieldsCommand.class);
    }

    @Test
    void testReschedule() {
        Duration delay = Duration.ofSeconds(10);
        YieldsNewSchedule payload = new YieldsNewSchedule(delay.toMillis());
        subject.givenSchedules(new Schedule(payload, "test", subject.getCurrentTime().plus(delay)))
                .whenTimeElapses(delay).expectNewSchedules(payload);
    }

    @Test
    void testScheduleOverride() {
        Duration delay = Duration.ofSeconds(10);
        Object expected = new YieldsCommand("original");
        Object notExpected = new YieldsCommand("override");
        subject.givenSchedules(new Schedule(expected, "test", subject.getCurrentTime().plus(delay)))
                .givenSchedules(
                        new Schedule(notExpected, "test", subject.getCurrentTime().plus(delay).minusSeconds(1)))
                .whenTimeElapses(delay).expectOnlyCommands("override");
    }

    @Test
    void testNoAutomaticRescheduleBeforeDeadline() {
        subject.givenElapsedTime(Duration.ofMillis(500)).whenExecuting(fc -> {
        }).expectNoNewSchedules();
    }

    @Test
    void testAutomaticReschedule() {
        subject.givenElapsedTime(Duration.ofMillis(500))
                .whenTimeElapses(Duration.ofMillis(1000)).expectOnlyNewSchedules(new PeriodicSchedule());
    }

    @Test
    void testAutomaticPeriodicSchedule() {
        subject.whenTimeElapses(Duration.ofMillis(1000))
                .expectNewSchedules(PeriodicSchedule.class);
    }

    @Test
    void testAutomaticPeriodicScheduleWithMethodAnnotation() {
        TestFixture.create(new MethodPeriodicHandler())
                .whenTimeElapses(Duration.ofMillis(1000)).expectNewSchedules(MethodPeriodicSchedule.class);
    }

    @Test
    void testNonAutomaticPeriodicSchedule() {
        subject.whenTimeElapses(Duration.ofMillis(1000))
                .expectNoNewSchedulesLike(NonAutomaticPeriodicSchedule.class);
    }

    @Test
    void testAlteredPayloadPeriodic() {
        TestFixture.create(new AlteredPayloadPeriodicHandler())
                .whenTimeElapses(Duration.ofMillis(1000)).expectOnlyNewSchedules(new YieldsAlteredSchedule(2));
    }

    @Test
    void testAlteredPayloadNonPeriodic() {
        subject = TestFixture.create(new AlteredPayloadNonPeriodicHandler());
        Instant deadline = subject.getCurrentTime().plusSeconds(1);
        subject.givenSchedules(new Schedule(new YieldsAlteredSchedule(), "test", deadline))
                .whenTimeAdvancesTo(deadline).expectOnlyNewSchedules(new YieldsAlteredSchedule(1));
    }

    @Test
    void testAlteredPayloadNonPeriodicReturningSchedule() {
        subject = TestFixture.create(new AlteredPayloadNonPeriodicHandlerReturningSchedule());
        Instant deadline = subject.getCurrentTime().plusSeconds(1);
        subject.givenSchedules(new Schedule(new YieldsAlteredSchedule(), "test", deadline))
                .whenTimeAdvancesTo(deadline).expectOnlyNewSchedules(new YieldsAlteredSchedule(1));
    }

    @Test
    void testInterfacePeriodicHandler() {
        TestFixture.create(new InterfacePeriodicHandler())
                .whenTimeElapses(Duration.ofMillis(1000))
                .expectNewSchedules(PeriodicScheduleFromInterface.class);
    }

    @Test
    void testGetSchedule() {
        Schedule schedule = new Schedule(new YieldsCommand("bla"), "test",
                                         subject.getCurrentTime().plusSeconds(10));
        subject.givenSchedules(schedule)
                .whenApplying(fc -> fc.scheduler().getSchedule("test").orElse(null))
                .expectResult(schedule);
    }

    @Test
    void testScheduledCommand() {
        Instant deadline = subject.getCurrentTime().plusSeconds(10);
        subject.givenScheduledCommands(
                        new Schedule("some command", "testId", deadline).addMetadata("a", "b"))
                .whenTimeAdvancesTo(deadline)
                .expectCommands("some command");
    }

    @Test
    void testScheduledCommand_async() {
        Instant deadline = Instant.now().plusSeconds(10);
        TestFixture.createAsync(new CommandHandler())
                .givenScheduledCommands(new Schedule("some command", deadline).addMetadata("a", "b"))
                .whenTimeAdvancesTo(deadline)
                .expectCommands("some command");
    }

    @Test
    void testScheduledCommandCancellation() {
        Instant deadline = Instant.now().plusSeconds(10);
        String scheduleId = "testId";
        subject.givenScheduledCommands(new Schedule("some command", scheduleId, deadline))
                .given(fc -> FluxCapacitor.cancelSchedule(scheduleId))
                .whenTimeAdvancesTo(deadline).expectNoCommands();
    }

    static class CommandHandler {
        @HandleCommand
        void handle(YieldsSchedule command) {
            FluxCapacitor.get().scheduler().schedule(command.getSchedule());
        }

        @HandleCommand
        void handle(String simpleCommand) {
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
        @Periodic(delay = 1000)
        void handle(MethodPeriodicSchedule schedule) {
        }
    }

    static class AlteredPayloadPeriodicHandler {
        @HandleSchedule
        @Periodic(delay = 1000)
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
                              subject.getCurrentTime().plusSeconds(10)));
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
    @Periodic(delay = 1, timeUnit = TimeUnit.SECONDS)
    static class PeriodicSchedule {
    }

    @Value
    static class CronSchedule {
    }

    @Value
    static class MethodPeriodicSchedule {
    }

    @Value
    @Periodic(delay = 1000, autoStart = false)
    static class NonAutomaticPeriodicSchedule {
    }

    @Value
    static class PeriodicScheduleFromInterface implements PeriodicInterface {
    }

    @Periodic(delay = 1000)
    interface PeriodicInterface {
    }

}
