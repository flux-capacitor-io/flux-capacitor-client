package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;

public interface SchedulingClient extends AutoCloseable {

    Awaitable schedule(ScheduledMessage... schedules);

    Awaitable cancelSchedule(String scheduleId);

    @Override
    void close();
}
