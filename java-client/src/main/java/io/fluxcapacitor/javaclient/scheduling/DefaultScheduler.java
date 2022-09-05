/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.scheduling;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;

import java.lang.reflect.Executable;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.indexFromTimestamp;

@AllArgsConstructor
public class DefaultScheduler implements Scheduler {

    private final SchedulingClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public void schedule(Schedule message, boolean ifAbsent) {
        try {
            message = (Schedule) dispatchInterceptor.interceptDispatch(message, SCHEDULE);
            SerializedMessage serializedMessage = dispatchInterceptor.modifySerializedMessage(
                    message.serialize(serializer), message, SCHEDULE);
            client.schedule(new SerializedSchedule(message.getScheduleId(),
                                                   message.getDeadline().toEpochMilli(),
                                                   serializedMessage, ifAbsent)).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to schedule message %s for %s", message.getPayload(),
                                                       message.getDeadline()), e);
        }
    }

    @Override
    public void scheduleCommand(Schedule command, boolean ifAbsent) {
        schedule(command.withPayload(new ScheduledCommand(command.serialize(serializer))), ifAbsent);
    }

    @Override
    public void cancelSchedule(String scheduleId) {
        try {
            client.cancelSchedule(scheduleId).await();
        } catch (Exception e) {
            throw new SchedulerException(String.format("Failed to cancel schedule with id %s", scheduleId), e);
        }
    }

    @Override
    public Optional<Schedule> getSchedule(String scheduleId) {
        return Optional.ofNullable(client.getSchedule(scheduleId)).flatMap(
                s -> serializer.deserializeMessages(Stream.of(s.getMessage()), SCHEDULE).findFirst()
                        .map(DeserializingMessage::toMessage).map(
                                m -> new Schedule(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp(),
                                                  s.getScheduleId(), Instant.ofEpochMilli(s.getTimestamp()))));
    }

    /*
        Only used by the TestFixture to simulate scheduling in a single thread
     */

    public Registration registerHandler(Object target, BiPredicate<Class<?>, Executable> handlerFilter) {
        return localHandlerRegistry.registerHandler(target, handlerFilter);
    }

    public void handleLocally(Schedule schedule) {
        var serializedMessage = schedule.serialize(serializer);
        serializedMessage.setIndex(indexFromTimestamp(schedule.getDeadline()));
        localHandlerRegistry.handle(new DeserializingMessage(
                serializedMessage, type -> serializer.convert(schedule.getPayload(), type), SCHEDULE));
    }
}
