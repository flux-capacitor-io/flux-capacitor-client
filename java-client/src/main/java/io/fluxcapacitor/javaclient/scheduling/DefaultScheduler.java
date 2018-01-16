package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import lombok.AllArgsConstructor;

import java.time.Instant;

@AllArgsConstructor
public class DefaultScheduler implements Scheduler {

    private final SchedulingClient client;
    private final Serializer serializer;

    @Override
    public void schedule(String scheduleId, Instant timestamp, Object payload, Metadata metadata) {
        try {
            client.schedule(new ScheduledMessage(scheduleId, timestamp.toEpochMilli(),
                                                 new SerializedMessage(serializer.serialize(payload),
                                                                                 metadata))).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to schedule message %s for %s", payload, timestamp), e);
        }
    }

    @Override
    public void cancelSchedule(String scheduleId) {
        try {
            client.cancelSchedule(scheduleId).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to cancel schedule with id %s", scheduleId), e);
        }
    }
}
