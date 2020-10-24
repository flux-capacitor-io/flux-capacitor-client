package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.scheduling.Schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.UUID;

public interface Given {

    When givenCommands(Object... commands);

    When givenDomainEvents(String aggregateId, Object... events);

    When givenEvents(Object... events);

    When given(Runnable condition);

    When givenSchedules(Schedule... schedules);

    default When givenExpiredSchedules(Object... schedules) {
        return givenSchedules(
                Arrays.stream(schedules).map(p -> new Schedule(p, UUID.randomUUID().toString(), getClock().instant()))
                        .toArray(Schedule[]::new));
    }

    default When givenNoPriorActivity() {
        return givenCommands();
    }

    Clock getClock();

    Given withClock(Clock clock);

    default Given withFixedTime(Instant time) {
        return withClock(Clock.fixed(time, ZoneId.systemDefault()));
    }

}
