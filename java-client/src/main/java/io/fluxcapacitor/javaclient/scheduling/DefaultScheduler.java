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
import io.fluxcapacitor.common.api.scheduling.ScheduledMessage;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.MessageSerializer;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;

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
}
