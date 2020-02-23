package io.fluxcapacitor.javaclient.scheduling.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public interface SupportsTimeTravel {
    void useClock(Clock clock);
    
    void advanceTimeBy(Duration duration);

    void advanceTimeTo(Instant timestamp);

}
