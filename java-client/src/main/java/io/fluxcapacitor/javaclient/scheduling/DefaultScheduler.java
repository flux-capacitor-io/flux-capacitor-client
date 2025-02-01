/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.scheduling.SerializedSchedule;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.SCHEDULE;
import static io.fluxcapacitor.javaclient.tracking.IndexUtils.indexFromTimestamp;

@AllArgsConstructor
public class DefaultScheduler implements Scheduler, HasLocalHandlers {

    private final SchedulingClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final DispatchInterceptor commandDispatchInterceptor;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public CompletableFuture<Void> schedule(Schedule message, boolean ifAbsent, Guarantee guarantee) {
        if (Entity.isLoading()) {
            return CompletableFuture.completedFuture(null);
        }
        message = (Schedule) dispatchInterceptor.interceptDispatch(message, SCHEDULE, null);
        if (message == null) {
            return CompletableFuture.completedFuture(null);
        }
        SerializedMessage serializedMessage = dispatchInterceptor.modifySerializedMessage(
                message.serialize(serializer), message, SCHEDULE, null);
        if (serializedMessage == null) {
            return CompletableFuture.completedFuture(null);
        }
        dispatchInterceptor.monitorDispatch(message, SCHEDULE, null);
        return client.schedule(guarantee, new SerializedSchedule(message.getScheduleId(),
                                               message.getDeadline().toEpochMilli(),
                                               serializedMessage, ifAbsent));
    }

    @Override
    public CompletableFuture<Void> scheduleCommand(Schedule schedule, boolean ifAbsent, Guarantee guarantee) {
        if (Entity.isLoading()) {
            return CompletableFuture.completedFuture(null);
        }
        var commandMessage = schedule.withMessageId(FluxCapacitor.currentIdentityProvider().nextTechnicalId());
        var intercepted = commandDispatchInterceptor.interceptDispatch(commandMessage, COMMAND, null);
        if (intercepted == null) {
            return CompletableFuture.completedFuture(null);
        }
        commandMessage = commandMessage.withPayload(intercepted.getPayload()).withMetadata(intercepted.getMetadata());
        SerializedMessage serializedCommand = commandDispatchInterceptor.modifySerializedMessage(
                commandMessage.serialize(serializer), commandMessage, COMMAND, null);
        if (serializedCommand == null) {
            return CompletableFuture.completedFuture(null);
        }
        return schedule(schedule.withPayload(new ScheduledCommand(serializedCommand))
                                .addMetadata("$commandType", schedule.getPayloadClass().getName()), ifAbsent, guarantee);
    }

    @Override
    public void cancelSchedule(String scheduleId) {
        try {
            if (Entity.isLoading()) {
                return;
            }
            client.cancelSchedule(scheduleId).get();
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

    @SneakyThrows
    public void handleLocally(Schedule schedule) {
        var serializedMessage = schedule.serialize(serializer);
        serializedMessage.setIndex(indexFromTimestamp(schedule.getDeadline()));
        var result = localHandlerRegistry.handle(new DeserializingMessage(
                serializedMessage, type -> serializer.convert(schedule.getPayload(), type), SCHEDULE, null));
        if (result.isPresent()) {
            result.get().get();
        }
    }
}
