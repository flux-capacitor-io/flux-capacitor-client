package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.javaclient.common.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface Scheduler {

    default void schedule(Object schedule, Instant deadline) {
        schedule(schedule, UUID.randomUUID().toString(), deadline);
    }

    default void schedule(Object schedule, Duration delay) {
        schedule(schedule, UUID.randomUUID().toString(), delay);
    }

    void schedule(Object schedule, String scheduleId, Duration delay);

    default void schedule(Object schedule, String scheduleId, Instant deadline) {
        if (schedule instanceof Message) {
            Message message = (Message) schedule;
            schedule(new Schedule(message.getPayload(), message.getMetadata(), message.getMessageId(),
                                  message.getTimestamp(), scheduleId, deadline));
        }
        schedule(new Schedule(schedule, scheduleId, deadline));
    }

    void schedule(Schedule message);

    void cancelSchedule(String scheduleId);

}
