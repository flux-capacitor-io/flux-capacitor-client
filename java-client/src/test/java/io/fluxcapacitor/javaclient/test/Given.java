package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.scheduling.Schedule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public interface Given {
    
    When givenCommands(Object... commands);

    When givenEvents(Object... events);
    
    When given(Runnable condition);
    
    When givenSchedules(Schedule... schedules);

    default When givenNoPriorActivity() {
        return givenCommands();
    }

    Clock getClock();
    
    Given withClock(Clock clock);

    default Given withFixedTime(Instant time) {
        return withClock(Clock.fixed(time, ZoneId.systemDefault()));
    }

}
