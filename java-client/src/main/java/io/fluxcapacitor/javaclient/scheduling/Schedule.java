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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.User;
import io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider;
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

/**
 * Represents a scheduled message to be delivered at a specific future time.
 * <p>
 * {@code Schedule} extends {@link Message} with a {@code scheduleId} and a {@code deadline} indicating when
 * the message should be delivered.
 * </p>
 *
 * <p>
 * It supports transformation and enrichment just like a regular {@link Message}, and includes convenience
 * methods for rescheduling.
 * </p>
 *
 * <h2>Typical Usage</h2>
 * <pre>{@code
 * new Schedule(new Reminder("water the plants"), Duration.ofHours(1));
 * }</pre>
 *
 * @see Message
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Schedule extends Message {

    /**
     * Metadata key for the schedule identifier.
     */
    public static String scheduleIdMetadataKey = "$scheduleId";

    /**
     * Unique identifier for this scheduled message. Used to replace, cancel, or retrieve the schedule.
     */
    @NonNull String scheduleId;

    /**
     * The time at which this scheduled message should be delivered.
     */
    @NonNull Instant deadline;

    /**
     * Creates a new schedule using the current identity provider for the {@code scheduleId} and a future deadline.
     *
     * @param payload  the message payload
     * @param deadline the delivery deadline
     */
    public Schedule(Object payload, Instant deadline) {
        this(payload, FluxCapacitor.currentIdentityProvider().nextTechnicalId(), deadline);
    }

    /**
     * Creates a new scheduled message with a given {@code scheduleId}.
     *
     * @param payload    the payload
     * @param scheduleId the unique schedule identifier
     * @param deadline   the delivery deadline
     */
    public Schedule(Object payload, String scheduleId, Instant deadline) {
        this(payload, Metadata.empty(), scheduleId, deadline);
    }

    /**
     * Full constructor specifying payload, metadata, scheduleId, and deadline.
     */
    public Schedule(Object payload, Metadata metadata, String scheduleId, Instant deadline) {
        super(payload, metadata);
        this.scheduleId = scheduleId;
        this.deadline = deadline.truncatedTo(MILLIS);
    }

    /**
     * Full constructor including message ID and timestamp.
     */
    @ConstructorProperties({"payload", "metadata", "messageId", "timestamp", "scheduleId", "deadline"})
    public Schedule(Object payload, Metadata metadata, String messageId, Instant timestamp,
                    String scheduleId, Instant deadline) {
        super(payload, metadata, messageId, timestamp);
        this.scheduleId = scheduleId;
        this.deadline = deadline.truncatedTo(MILLIS);
    }

    /**
     * Returns a new instance with the given payload.
     */
    @Override
    public Schedule withPayload(Object payload) {
        if (payload == getPayload()) {
            return this;
        }
        return new Schedule(payload, getMetadata(), getMessageId(), getTimestamp(), scheduleId, deadline);
    }

    /**
     * Returns a new instance with the given metadata.
     */
    @Override
    public Schedule withMetadata(Metadata metadata) {
        return new Schedule(getPayload(), metadata, getMessageId(), getTimestamp(), scheduleId, deadline);
    }

    /**
     * Returns a new instance with the given message ID.
     */
    @Override
    public Schedule withMessageId(String messageId) {
        return new Schedule(getPayload(), getMetadata(), messageId, getTimestamp(), scheduleId, deadline);
    }

    /**
     * Returns a new instance with the given timestamp.
     */
    @Override
    public Schedule withTimestamp(Instant timestamp) {
        return new Schedule(getPayload(), getMetadata(), getMessageId(), timestamp, scheduleId, deadline);
    }

    /**
     * Returns a new schedule with additional metadata.
     */
    @Override
    public Schedule addMetadata(Metadata metadata) {
        return (Schedule) super.addMetadata(metadata);
    }

    /**
     * Returns a new schedule with a single metadata entry added.
     */
    @Override
    public Schedule addMetadata(String key, Object value) {
        return (Schedule) super.addMetadata(key, value);
    }

    /**
     * Returns a new schedule with multiple metadata entries added.
     */
    @Override
    public Schedule addMetadata(Object... keyValues) {
        return (Schedule) super.addMetadata(keyValues);
    }

    /**
     * Returns a new schedule with all values from the given metadata map added.
     */
    @Override
    public Schedule addMetadata(Map<String, ?> values) {
        return (Schedule) super.addMetadata(values);
    }

    /**
     * Returns a new schedule with a {@link User} added to the metadata using the configured {@link UserProvider}.
     */
    @Override
    public Schedule addUser(User user) {
        return (Schedule) super.addUser(user);
    }

    /**
     * Returns a new {@code Schedule} with the same {@code scheduleId} and a new deadline offset by the given duration.
     *
     * @param duration duration to shift the schedule forward
     * @return rescheduled instance
     */
    public Schedule reschedule(Duration duration) {
        return new Schedule(getPayload(), getMetadata(), FluxCapacitor.currentIdentityProvider().nextTechnicalId(), currentTime(),
                            scheduleId, deadline.plus(duration));
    }
}
