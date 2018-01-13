package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Metadata;

import java.time.Instant;

public interface Scheduler {

    default Awaitable schedule(String scheduleId, Instant timestamp, Object payload) {
        return schedule(scheduleId, timestamp, payload, Metadata.empty());
    }

    Awaitable schedule(String scheduleId, Instant timestamp, Object payload, Metadata metadata);

    Awaitable cancelSchedule(String scheduleId);

}
