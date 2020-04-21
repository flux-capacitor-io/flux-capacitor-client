package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.fluxcapacitor.common.IndexUtils.indexFromTimestamp;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;

@AllArgsConstructor
public class DefaultScheduler implements Scheduler {

    private final SchedulingClient client;
    private final MessageSerializer serializer;
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public void schedule(Schedule message) {
        try {
            SerializedMessage serializedMessage = serializer.serialize(message);
            tryHandleLocally(message, serializedMessage);
            client.schedule(new ScheduledMessage(message.getScheduleId(),
                                                 message.getDeadline().toEpochMilli(),
                                                 serializedMessage)).await();
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

    public Registration registerHandler(Object target, HandlerConfiguration<DeserializingMessage> handlerConfiguration) {
        return localHandlerRegistry.registerHandler(target, handlerConfiguration);
    }

    protected void tryHandleLocally(Schedule schedule, SerializedMessage serializedMessage) {
        if (schedule.isExpired(client.getClock())) {
            serializedMessage.setIndex(indexFromTimestamp(schedule.getDeadline()));
            localHandlerRegistry.handle(schedule.getPayload(), serializedMessage);
        }
    }
}
