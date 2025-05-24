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

import io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Declares a message (typically a {@link Schedule}) or its handler method as part of a periodic schedule.
 * <p>
 * Periodic schedules are automatically rescheduled after each invocation using either a fixed delay or a cron
 * expression. This enables use cases like polling APIs, triggering maintenance routines, or recurring status checks.
 * <p>
 * This annotation can be placed on either:
 * <ul>
 *     <li>The payload class of a {@link Schedule}, marking it as inherently periodic</li>
 *     <li>A handler method annotated with {@link HandleSchedule}, overriding periodic behavior</li>
 * </ul>
 *
 * <h2>Rescheduling Behavior</h2>
 * <ul>
 *     <li>If {@link #cron()} is specified, it defines when the schedule should run (overrides {@link #delay()})</li>
 *     <li>If {@link #delay()} is used and {@code cron} is blank, the schedule runs with fixed delays</li>
 *     <li>The {@link #initialDelay()} (if ≥ 0) applies once when the schedule is first started (only if {@link #autoStart()} is {@code true})</li>
 * </ul>
 *
 * <h2>Automatic Start</h2>
 * By default, the schedule is automatically started when the message is handled or the system starts. Set {@link #autoStart()} to {@code false} to disable this behavior.
 *
 * <h2>Error Handling</h2>
 * If the schedule handler throws an exception:
 * <ul>
 *     <li>If {@link #continueOnError()} is {@code true} (default), the schedule continues</li>
 *     <li>If {@link #delayAfterError()} is set (≥ 0), it overrides the normal delay after an error</li>
 *     <li>If {@code continueOnError} is {@code false}, the schedule is stopped after an error</li>
 * </ul>
 *
 * <h2>Stopping a Periodic Schedule</h2>
 * To stop a periodic schedule explicitly from a handler, throw {@link io.fluxcapacitor.javaclient.scheduling.CancelPeriodic}.
 * Returning {@code null} from the handler method does <strong>not</strong> stop the schedule — it simply continues with the default rescheduling.
 *
 * <h2>Interaction with {@link HandleSchedule}</h2>
 * When used with a {@code @HandleSchedule} method, the return value influences scheduling:
 * <ul>
 *     <li><b>{@code null} or {@code void}</b>: continue scheduling using the {@link Periodic} settings.</li>
 *     <li><b>{@link java.time.Duration} or {@link java.time.Instant}</b>: overrides the next schedule delay or time.</li>
 *     <li><b>A new payload</b>: schedules the returned object as the next scheduled message. If this new payload is annotated
 *         with {@link Periodic}, the periodic settings on that payload take precedence.</li>
 *     <li>Returning a new {@link io.fluxcapacitor.javaclient.scheduling.Schedule} reschedules that message using the specified payload and deadline.</li>
 * </ul>
 *
 * <h3>Example: Schedule Every 5 Minutes</h3>
 * <pre>{@code
 * @Value
 * @Periodic(delay = 5, timeUnit = TimeUnit.MINUTES)
 * public class RefreshData { ... }
 * }</pre>
 *
 * <h3>Example: Weekly Schedule via Cron</h3>
 * <pre>{@code
 * @Periodic(cron = "0 0 * * MON", timeZone = "Europe/Amsterdam")
 * @HandleSchedule
 * void weeklySync(Schedule schedule) {
 *     ...
 * }
 * }</pre>
 *
 * @see HandleSchedule
 * @see Schedule
 * @see io.fluxcapacitor.javaclient.scheduling.CancelPeriodic
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Periodic {
    /**
     * Special expression that can be used to disable automatic periodic scheduling if passed to {@link #cron()}. If the
     * schedule was already running and is disabled later on using this expression, any previously scheduled messages
     * will be ignored.
     */
    String DISABLED = "-";

    /**
     * Define a unix-like cron expression. If a cron value is specified the {@link #delay()} in milliseconds is
     * ignored.
     * <p>
     * The fields read from left to right are interpreted as follows.
     * <ul>
     * <li>minute</li>
     * <li>hour</li>
     * <li>day of month</li>
     * <li>month</li>
     * <li>day of week</li>
     * </ul>
     * <p>
     * For example, {@code "0 * * * MON-FRI"} means at the start of every hour on weekdays.
     * <p>
     * It is possible to refer to an application property, e.g. by specifying `${someFetchSchedule}` as cron value. To
     * disable the schedule altogether if the property is *not* set, specify `${someFetchSchedule:-}`.
     *
     * @see CronExpression for more info on how the string is parsed.
     */
    String cron() default "";

    /**
     * A time zone id for which the cron expression will be resolved. Defaults to UTC.
     */
    String timeZone() default "UTC";

    /**
     * Returns the id of the periodic schedule. Defaults to the fully qualified name of the schedule class.
     */
    String scheduleId() default "";

    /**
     * Returns true if this periodic schedule should be automatically started if it's not already active. Defaults to
     * {@code true}.
     */
    boolean autoStart() default true;

    /**
     * Returns the schedule delay in {@link #timeUnit()} units. Only used if {@link #cron()} is blank, in which case
     * this value should be a positive number.
     */
    long delay() default -1L;

    /**
     * Returns true if the schedule should continue after an error. Defaults to {@code true}.
     */
    boolean continueOnError() default true;

    /**
     * Returns the schedule delay in {@link #timeUnit()} units after handling of the last schedule gave rise to an
     * exception.
     * <p>
     * If this value is smaller than 0 (the default) this setting is ignored and the schedule will continue as if the
     * exception hadn't been triggered. If {@link #continueOnError()} is false, this setting is ignored as well.
     */
    long delayAfterError() default -1L;

    /**
     * Returns the unit for {@link #delay()} and {@link #initialDelay()}. Defaults to {@link TimeUnit#MILLISECONDS}.
     */
    TimeUnit timeUnit() default MILLISECONDS;

    /**
     * Returns the initial schedule delay. Only relevant when {@link #autoStart()} is true. If initialDelay is negative,
     * the initial schedule will fire after the default delay (configured either via {@link #cron()} or
     * {@link #delay()}).
     */
    long initialDelay() default 0;
}
