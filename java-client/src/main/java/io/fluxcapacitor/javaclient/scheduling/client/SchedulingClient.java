package io.fluxcapacitor.javaclient.scheduling.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;

public interface SchedulingClient {

    Awaitable schedule(ScheduledMessage... schedules);

    Awaitable cancelSchedule(String scheduleId);

}
