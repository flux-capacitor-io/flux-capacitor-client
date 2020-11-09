package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;

import java.time.Clock;
import java.time.Duration;

import static io.fluxcapacitor.common.IndexUtils.indexFromTimestamp;

@AllArgsConstructor
public class DefaultScheduler implements Scheduler {

    private final SchedulingClient client;
    private final MessageSerializer serializer;
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public void schedule(Schedule message) {
        try {
            SerializedMessage serializedMessage = serializer.serialize(message);
            client.schedule(new ScheduledMessage(message.getScheduleId(),
                                                 message.getDeadline().toEpochMilli(),
                                                 serializedMessage)).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to schedule message %s for %s", message.getPayload(),
                                                       message.getDeadline()), e);
        }
    }

    @Override
    public void schedule(Object schedule, String scheduleId, Duration delay) {
        schedule(schedule, scheduleId, clock().instant().plus(delay));
    }

    @Override
    public void cancelSchedule(String scheduleId) {
        try {
            client.cancelSchedule(scheduleId).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to cancel schedule with id %s", scheduleId), e);
        }
    }

    /*
        Only used by the TestFixture to simulate scheduling in a single thread
     */

    public Registration registerHandler(Object target, HandlerConfiguration<DeserializingMessage> handlerConfiguration) {
        return localHandlerRegistry.registerHandler(target, handlerConfiguration);
    }

    public void handleLocally(Schedule schedule, SerializedMessage serializedMessage) {
        serializedMessage.setIndex(indexFromTimestamp(schedule.getDeadline()));
        localHandlerRegistry.handle(schedule.getPayload(), serializedMessage);
    }

    private static Clock clock() {
        return FluxCapacitor.getOptionally().map(FluxCapacitor::clock).orElse(Clock.systemUTC());
    }
}
