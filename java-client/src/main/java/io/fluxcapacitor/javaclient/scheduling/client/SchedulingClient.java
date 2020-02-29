package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;

import java.time.Clock;

public interface SchedulingClient extends AutoCloseable {

    Awaitable schedule(ScheduledMessage... schedules);

    Awaitable cancelSchedule(String scheduleId);
    
    default Clock getClock() {
        return Clock.systemUTC();
    }

    @Override
    void close();
}
