package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.javaclient.scheduling.Schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;

public interface When {

    Then whenCommand(Object command);

    Then when(Runnable task);

    Then whenApplying(Callable<?> task);

    Then whenTimeAdvancesTo(Instant instant);

    Then whenTimeElapses(Duration duration);

    Then whenQuery(Object query);
    
    Then whenEvent(Object event);
    
    /*
        Continued
     */

    When andGiven(Runnable runnable);

    When andGivenCommands(Object... commands);

    When andGivenEvents(Object... events);

    When andGivenSchedules(Schedule... schedules);

    When andThenTimeAdvancesTo(Instant instant);

    When andThenTimeElapses(Duration duration);
}
