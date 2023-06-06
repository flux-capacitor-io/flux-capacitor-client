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
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.beans.ConstructorProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentTime;
import static java.time.temporal.ChronoUnit.MILLIS;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Schedule extends Message {
    public static String scheduleIdMetadataKey = "$scheduleId";

    @NonNull String scheduleId;
    @NonNull Instant deadline;

    public Schedule(Object payload, Instant deadline) {
        this(payload, FluxCapacitor.currentIdentityProvider().nextTechnicalId(), deadline);
    }

    public Schedule(Object payload, String scheduleId, Instant deadline) {
        this(payload, Metadata.empty(), scheduleId, deadline);
    }

    public Schedule(Object payload, Metadata metadata, String scheduleId, Instant deadline) {
        super(payload, metadata);
        this.scheduleId = scheduleId;
        this.deadline = deadline.truncatedTo(MILLIS);
    }

    public Schedule(Message m) {
        this(m.getPayload(), m.getMetadata(), m.getMessageId(), m.getTimestamp(),
             m.getMetadata().get(Schedule.scheduleIdMetadataKey), currentTime());
    }

    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp", "scheduleId", "deadline"})
    public Schedule(Object payload, Metadata metadata, String messageId, Instant timestamp,
                    String scheduleId, Instant deadline) {
        super(payload, metadata, messageId, timestamp);
        this.scheduleId = scheduleId;
        this.deadline = deadline.truncatedTo(MILLIS);
    }

    @Override
    public Schedule withPayload(Object payload) {
        return new Schedule(payload, getMetadata(), getMessageId(), getTimestamp(), scheduleId, deadline);
    }

    @Override
    public Schedule withMetadata(Metadata metadata) {
        return new Schedule(getPayload(), metadata, getMessageId(), getTimestamp(), scheduleId, deadline);
    }

    @Override
    public Schedule addMetadata(Metadata metadata) {
        return (Schedule) super.addMetadata(metadata);
    }

    @Override
    public Schedule addMetadata(String key, Object value) {
        return (Schedule) super.addMetadata(key, value);
    }

    @Override
    public Schedule addMetadata(Object... keyValues) {
        return (Schedule) super.addMetadata(keyValues);
    }

    @Override
    public Schedule addMetadata(Map<String, ?> values) {
        return (Schedule) super.addMetadata(values);
    }

    @Override
    public Schedule addUser(User user) {
        return (Schedule) super.addUser(user);
    }

    public Schedule reschedule(Duration duration) {
        return new Schedule(getPayload(), getMetadata(), FluxCapacitor.currentIdentityProvider().nextTechnicalId(), currentTime(),
                            scheduleId, deadline.plus(duration));
    }
}
