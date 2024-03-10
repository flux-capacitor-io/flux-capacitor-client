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
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentTime;

public interface Scheduler {

    default String schedule(Object schedule, Instant deadline) {
        String scheduleId = FluxCapacitor.currentIdentityProvider().nextTechnicalId();
        schedule(schedule, scheduleId, deadline);
        return scheduleId;
    }

    default String schedule(Object schedule, Duration delay) {
        return schedule(schedule, currentTime().plus(delay));
    }

    default String schedule(Object schedule, String cronSchedule) {
        return schedule(schedule, nextDeadline(cronSchedule));
    }

    default void schedule(Object schedule, String scheduleId, Duration delay) {
        schedule(schedule, scheduleId, currentTime().plus(delay));
    }

    default void schedule(Object schedule, String scheduleId, String cronSchedule) {
        schedule(schedule, scheduleId, nextDeadline(cronSchedule));
    }

    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, Instant deadline) {
        schedule(new Message(schedulePayload, metadata), scheduleId, deadline);
    }

    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, Duration delay) {
        schedule(new Message(schedulePayload, metadata), scheduleId, delay);
    }

    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, String cronSchedule) {
        schedule(new Message(schedulePayload, metadata), scheduleId, nextDeadline(cronSchedule));
    }

    default void schedule(Object schedule, String scheduleId, Instant deadline) {
        if (schedule instanceof Message message) {
            schedule(new Schedule(message.getPayload(), message.getMetadata(), message.getMessageId(),
                                  message.getTimestamp(), scheduleId, deadline));
        } else {
            schedule(new Schedule(schedule, scheduleId, deadline));
        }
    }

    default void schedule(Schedule message) {
        schedule(message, false);
    }

    @SneakyThrows
    default void schedule(Schedule message, boolean ifAbsent) {
        try {
            schedule(message, ifAbsent, Guarantee.SENT).get();
        } catch (Throwable e) {
            throw new SchedulerException(String.format("Failed to schedule message %s for %s", message.getPayload(),
                                                       message.getDeadline()), e);
        }
    }

    CompletableFuture<Void> schedule(Schedule message, boolean ifAbsent, Guarantee guarantee);

    default String scheduleCommand(Object schedule, Instant deadline) {
        String scheduleId = FluxCapacitor.currentIdentityProvider().nextTechnicalId();
        scheduleCommand(schedule, scheduleId, deadline);
        return scheduleId;
    }

    default String scheduleCommand(Object schedule, Duration delay) {
        return scheduleCommand(schedule, currentTime().plus(delay));
    }

    default String scheduleCommand(Object schedule, String cronSchedule) {
        return scheduleCommand(schedule, nextDeadline(cronSchedule));
    }

    default void scheduleCommand(Object schedule, String scheduleId, Duration delay) {
        scheduleCommand(schedule, scheduleId, currentTime().plus(delay));
    }

    default void scheduleCommand(Object schedule, String scheduleId, String cronSchedule) {
        scheduleCommand(schedule, scheduleId, nextDeadline(cronSchedule));
    }

    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, Instant deadline) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, deadline);
    }

    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, Duration delay) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, delay);
    }

    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, String cronSchedule) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, nextDeadline(cronSchedule));
    }

    default void scheduleCommand(Object schedule, String scheduleId, Instant deadline) {
        if (schedule instanceof Message message) {
            scheduleCommand(new Schedule(message.getPayload(), message.getMetadata(), message.getMessageId(),
                                  message.getTimestamp(), scheduleId, deadline));
        } else {
            scheduleCommand(new Schedule(schedule, scheduleId, deadline));
        }
    }

    default void scheduleCommand(Schedule message) {
        scheduleCommand(message, false);
    }

    default void scheduleCommand(Schedule message, boolean ifAbsent) {
        try {
            scheduleCommand(message, ifAbsent, Guarantee.SENT).get();
        } catch (Throwable e) {
            throw new SchedulerException(String.format("Failed to schedule command %s for %s", message.getPayload(),
                                                       message.getDeadline()), e);
        }
    }

    CompletableFuture<Void> scheduleCommand(Schedule message, boolean ifAbsent, Guarantee guarantee);

    void cancelSchedule(String scheduleId);

    Optional<Schedule> getSchedule(String scheduleId);

    private static Instant nextDeadline(String cronSchedule) {
        return CronExpression.parseCronExpression(cronSchedule).nextTimeAfter(
                FluxCapacitor.currentTime().atZone(ZoneId.of("UTC"))).toInstant();
    }
}
