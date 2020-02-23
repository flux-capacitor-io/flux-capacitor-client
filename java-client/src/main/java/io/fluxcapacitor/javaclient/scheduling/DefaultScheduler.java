package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DefaultScheduler implements Scheduler {

    private final SchedulingClient client;
    private final MessageSerializer serializer;

    @Override
    public void schedule(Schedule message) {
        try {
            client.schedule(new ScheduledMessage(message.getScheduleId(),
                                                 message.getDeadline().toEpochMilli(),
                                                 serializer.serialize(message))).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to schedule message %s for %s", message.getPayload(), 
                                                       message.getDeadline()), e);
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
