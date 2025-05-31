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
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import lombok.SneakyThrows;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentTime;
import static io.micrometer.common.util.StringUtils.isBlank;

/**
 * Interface for scheduling deferred or periodic execution of messages in the Flux Capacitor platform.
 * <p>
 * The {@code MessageScheduler} provides functionality for:
 * <ul>
 *   <li><strong>Deferring arbitrary payloads</strong> (to be handled via {@link io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule}).</li>
 *   <li><strong>Scheduling commands</strong> (to be handled via standard {@code @HandleCommand} handlers).</li>
 *   <li><strong>Recurring execution</strong> using cron expressions via the {@link Periodic}
 *       annotation.</li>
 * </ul>
 *
 * <h2>Scheduling semantics</h2>
 * <p>When using {@code schedule(...)}:
 * <ul>
 *   <li>Scheduled payloads are delivered to handler methods annotated with {@code @HandleSchedule}.</li>
 *   <li>Used when the intention is to invoke scheduling-specific logic or workflows.</li>
 * </ul>
 * <p>
 * When using {@code scheduleCommand(...)}:
 * <ul>
 *   <li>The scheduled payload will be dispatched as a command at the configured deadline.</li>
 *   <li>Handlers annotated with {@code @HandleCommand} will receive the message, just like normal commands.</li>
 *   <li>This is useful for scenarios where both immediate and delayed invocation use the same handler logic.</li>
 * </ul>
 *
 * <h2>Schedule identity</h2>
 * <p>All schedules are identified by a {@code scheduleId}. By default, one is generated unless explicitly passed.
 * If a schedule with the same ID already exists:
 * <ul>
 *   <li>It is replaced by default.</li>
 *   <li>Use {@code ifAbsent = true} to ensure the schedule is only created if it does not already exist.</li>
 * </ul>
 *
 * <h2>Typical usage</h2>
 * This interface underpins the static helpers in {@link io.fluxcapacitor.javaclient.FluxCapacitor}, such as:
 * <pre>
 *     FluxCapacitor.schedule(myPayload, Duration.ofMinutes(5));
 *     FluxCapacitor.scheduleCommand(myCommand, Instant.now().plusSeconds(10));
 * </pre>
 *
 * @see io.fluxcapacitor.javaclient.scheduling.Schedule
 * @see Periodic
 * @see io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule
 * @see ScheduledCommandHandler
 */
public interface MessageScheduler {

    /**
     * Schedule a periodic message using the {@code @Periodic} annotation on its class, using the {@link Guarantee#SENT}
     * guarantee.
     *
     * @param value the payload to schedule periodically
     * @return the schedule ID
     * @throws IllegalArgumentException if the annotation is missing or misconfigured
     */
    default String schedulePeriodic(Object value) {
        return schedulePeriodic(value, null);
    }

    /**
     * Schedule a periodic message using the given ID and the {@code @Periodic} annotation, using the
     * {@link Guarantee#SENT} guarantee.
     *
     * @param value      the payload to schedule periodically
     * @param scheduleId a custom ID or null to generate one
     * @return the effective schedule ID
     */
    default String schedulePeriodic(Object value, String scheduleId) {
        var periodic = ReflectionUtils.<Periodic>getTypeAnnotation(
                value instanceof Message m ? m.getPayloadClass() : value.getClass(), Periodic.class);
        if (periodic == null) {
            throw new IllegalArgumentException("Could not determine when to schedule this value");
        }
        Instant nextDeadline = Optional.ofNullable(nextDeadline(periodic.cron(), periodic.timeZone())).orElseGet(
                () -> FluxCapacitor.currentTime().plusMillis(periodic.timeUnit().toMillis(
                        periodic.initialDelay() < 0 ? periodic.delay() : periodic.initialDelay())));
        schedule(value,
                 Optional.ofNullable(scheduleId).or(() -> Optional.of(periodic.scheduleId()).filter(s -> !s.isBlank()))
                         .orElseGet(FluxCapacitor::generateId),
                 nextDeadline);
        return scheduleId;
    }

    /**
     * Schedule a message to be triggered at the given deadline, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule the message to schedule
     * @param deadline the absolute time to trigger the message
     * @return the generated schedule ID
     */
    default String schedule(Object schedule, Instant deadline) {
        String scheduleId = FluxCapacitor.currentIdentityProvider().nextTechnicalId();
        schedule(schedule, scheduleId, deadline);
        return scheduleId;
    }

    /**
     * Schedule a message using a delay from the current time, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule the message to schedule
     * @param delay    delay duration until the schedule triggers
     * @return the generated schedule ID
     */
    default String schedule(Object schedule, Duration delay) {
        return schedule(schedule, currentTime().plus(delay));
    }

    /**
     * Schedule a message with a custom ID using a delay.
     *
     * @param schedule   the message to schedule
     * @param scheduleId the unique ID of the schedule
     * @param delay      the delay until triggering
     */
    default void schedule(Object schedule, String scheduleId, Duration delay) {
        schedule(schedule, scheduleId, currentTime().plus(delay));
    }

    /**
     * Schedule a message with payload and metadata, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedulePayload the message payload
     * @param metadata        metadata to attach
     * @param scheduleId      the unique schedule ID
     * @param deadline        the deadline for triggering the schedule
     */
    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, Instant deadline) {
        schedule(new Message(schedulePayload, metadata), scheduleId, deadline);
    }

    /**
     * Schedule a message with payload and metadata using a delay, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedulePayload the message payload
     * @param metadata        metadata to attach
     * @param scheduleId      the schedule ID
     * @param delay           delay from now until triggering
     */
    default void schedule(Object schedulePayload, Metadata metadata, String scheduleId, Duration delay) {
        schedule(new Message(schedulePayload, metadata), scheduleId, delay);
    }

    /**
     * Schedule a message with the given ID and deadline, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule   the object to schedule
     * @param scheduleId unique schedule ID
     * @param deadline   the absolute time at which the schedule should trigger
     */
    default void schedule(Object schedule, String scheduleId, Instant deadline) {
        if (schedule instanceof Message message) {
            schedule(new Schedule(message.getPayload(), message.getMetadata(), message.getMessageId(),
                                  message.getTimestamp(), scheduleId, deadline));
        } else {
            schedule(new Schedule(schedule, scheduleId, deadline));
        }
    }

    /**
     * Schedule a message object (typically of type {@link Schedule}) for execution, using the {@link Guarantee#SENT}
     * guarantee.
     *
     * @param message the message to schedule
     */
    default void schedule(Schedule message) {
        schedule(message, false);
    }

    /**
     * Schedule a message, optionally skipping if already present, using the {@link Guarantee#SENT} guarantee.
     *
     * @param message  the schedule message
     * @param ifAbsent whether to skip scheduling if an existing schedule is present
     */
    @SneakyThrows
    default void schedule(Schedule message, boolean ifAbsent) {
        try {
            schedule(message, ifAbsent, Guarantee.SENT).get();
        } catch (Throwable e) {
            throw new SchedulerException(String.format("Failed to schedule message %s for %s", message.getPayload(),
                                                       message.getDeadline()), e);
        }
    }

    /**
     * Schedule the given {@link Schedule} object, optionally skipping if already present, using the specified
     * guarantee.
     *
     * @param message   the schedule message
     * @param ifAbsent  only schedule if not already scheduled
     * @param guarantee the delivery guarantee to use
     * @return a CompletableFuture completing when the message is successfully scheduled
     */
    CompletableFuture<Void> schedule(Schedule message, boolean ifAbsent, Guarantee guarantee);

    /**
     * Schedule a command message for future execution. This is similar to {@link #schedule} but ensures the message is
     * dispatched as a command, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule the command to schedule
     * @param deadline the deadline for execution
     * @return the generated schedule ID
     */
    default String scheduleCommand(Object schedule, Instant deadline) {
        String scheduleId = FluxCapacitor.currentIdentityProvider().nextTechnicalId();
        scheduleCommand(schedule, scheduleId, deadline);
        return scheduleId;
    }

    /**
     * Schedule a command to execute after given delay, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule the command to schedule
     * @param delay    delay until execution
     * @return the schedule ID
     */
    default String scheduleCommand(Object schedule, Duration delay) {
        return scheduleCommand(schedule, currentTime().plus(delay));
    }

    /**
     * Schedule a command with the given ID and delay, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule   the command to schedule
     * @param scheduleId schedule ID
     * @param delay      delay until execution
     */
    default void scheduleCommand(Object schedule, String scheduleId, Duration delay) {
        scheduleCommand(schedule, scheduleId, currentTime().plus(delay));
    }

    /**
     * Schedule a command message with attached metadata, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedulePayload payload of the command
     * @param metadata        metadata to attach
     * @param scheduleId      schedule ID
     * @param deadline        execution deadline
     */
    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, Instant deadline) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, deadline);
    }

    /**
     * Schedule a command with metadata and delay, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedulePayload payload to schedule
     * @param metadata        metadata to attach
     * @param scheduleId      schedule ID
     * @param delay           delay duration
     */
    default void scheduleCommand(Object schedulePayload, Metadata metadata, String scheduleId, Duration delay) {
        scheduleCommand(new Message(schedulePayload, metadata), scheduleId, delay);
    }

    /**
     * Schedule a command using a specific deadline, using the {@link Guarantee#SENT} guarantee.
     *
     * @param schedule   the command object or message
     * @param scheduleId the schedule ID
     * @param deadline   deadline for triggering the schedule
     */
    default void scheduleCommand(Object schedule, String scheduleId, Instant deadline) {
        if (schedule instanceof Message message) {
            scheduleCommand(new Schedule(message.getPayload(), message.getMetadata(), message.getMessageId(),
                                         message.getTimestamp(), scheduleId, deadline));
        } else {
            scheduleCommand(new Schedule(schedule, scheduleId, deadline));
        }
    }

    /**
     * Schedule a command message using the given scheduling settings, using the {@link Guarantee#SENT} guarantee.
     *
     * @param message the command message
     */
    default void scheduleCommand(Schedule message) {
        scheduleCommand(message, false);
    }

    /**
     * Schedule a command using the given scheduling settings if no other with same ID exists, using the
     * {@link Guarantee#SENT} guarantee.
     *
     * @param message  the command schedule
     * @param ifAbsent whether to skip if already scheduled
     */
    default void scheduleCommand(Schedule message, boolean ifAbsent) {
        try {
            scheduleCommand(message, ifAbsent, Guarantee.SENT).get();
        } catch (Throwable e) {
            throw new SchedulerException(String.format("Failed to schedule command %s for %s", message.getPayload(),
                                                       message.getDeadline()), e);
        }
    }

    /**
     * Schedule a command using the given scheduling settings, using the provided {@link Guarantee}.
     *
     * @param message   the command schedule
     * @param ifAbsent  skip if existing schedule is present
     * @param guarantee the delivery guarantee to apply
     * @return a future indicating when the command is scheduled
     */
    CompletableFuture<Void> scheduleCommand(Schedule message, boolean ifAbsent, Guarantee guarantee);

    /**
     * Cancel a previously scheduled message or command by ID.
     *
     * @param scheduleId the ID of the schedule to cancel
     */
    void cancelSchedule(String scheduleId);

    /**
     * Look up an existing schedule.
     *
     * @param scheduleId the ID of the schedule
     * @return the schedule if found
     */
    Optional<Schedule> getSchedule(String scheduleId);

    private static Instant nextDeadline(String cronSchedule, String timeZone) {
        if (isBlank(cronSchedule)) {
            return null;
        }
        return CronExpression.parseCronExpression(cronSchedule).nextTimeAfter(
                FluxCapacitor.currentTime().atZone(ZoneId.of(timeZone))).toInstant();
    }
}
