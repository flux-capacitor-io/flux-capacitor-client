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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

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

    default void schedule(Object schedule, String scheduleId, Duration delay) {
        schedule(schedule, scheduleId, currentTime().plus(delay));
    }

    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, Instant deadline) {
        schedule(new Message(schedulePayload, metadata), scheduleId, deadline);
    }

    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, Duration delay) {
        schedule(new Message(schedulePayload, metadata), scheduleId, delay);
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

    void schedule(Schedule message, boolean ifAbsent);

    default String scheduleCommand(Object schedule, Instant deadline) {
        String scheduleId = FluxCapacitor.currentIdentityProvider().nextTechnicalId();
        scheduleCommand(schedule, scheduleId, deadline);
        return scheduleId;
    }

    default String scheduleCommand(Object schedule, Duration delay) {
        return scheduleCommand(schedule, currentTime().plus(delay));
    }

    default void scheduleCommand(Object schedule, String scheduleId, Duration delay) {
        scheduleCommand(schedule, scheduleId, currentTime().plus(delay));
    }

    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, Instant deadline) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, deadline);
    }

    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, Duration delay) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, delay);
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

    void scheduleCommand(Schedule message, boolean ifAbsent);

    void cancelSchedule(String scheduleId);

    Optional<Schedule> getSchedule(String scheduleId);
}
