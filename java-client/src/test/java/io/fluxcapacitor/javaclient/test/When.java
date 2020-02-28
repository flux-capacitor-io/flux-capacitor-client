package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.scheduling.Schedule;

import java.time.Duration;
import java.time.Instant;

public interface When {
    When andGiven(Runnable runnable);
    
    When andGivenCommands(Object... commands);

    When andGivenEvents(Object... events);

    When andGivenSchedules(Schedule... schedules);
    
    When andThenTimeAdvancesTo(Instant instant);
    
    When andThenTimeElapses(Duration duration);

    Then whenCommand(Object command);

    Then whenEvent(Object event);

    Then whenQuery(Object query);

    Then when(Runnable task);
    
    Then whenTimeAdvancesTo(Instant instant);
    
    Then whenTimeElapses(Duration duration);
}
