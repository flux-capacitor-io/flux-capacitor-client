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
 * Annotation placed on methods annotated with {@link HandleSchedule} or on the payload class of a Schedule. If this
 * annotation is present the same payload will be rescheduled using the configured delay each time after handling.
 * <p>
 * By default, the periodic schedule will also be automatically started if it isn't running yet.
 * <p>
 * Note, that if exception {@link CancelPeriodic} is thrown by the handler for a schedule, the periodic schedule will be
 * cancelled.
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
