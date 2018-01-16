package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.Metadata;

import java.time.Instant;

public interface Scheduler {

    default void schedule(String scheduleId, Instant timestamp, Object payload) {
        schedule(scheduleId, timestamp, payload, Metadata.empty());
    }

    void schedule(String scheduleId, Instant timestamp, Object payload, Metadata metadata);

    void cancelSchedule(String scheduleId);

}
