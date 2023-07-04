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
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Periodic {
    /**
     * Special expression that can be used to disable automatic periodic scheduling if passed to {@link #cron()}.
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
     *
     * @see CronExpression for more info on how the string is parsed.
     */
    String cron() default "";

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
     * Returns true if the schedule should continue after an error. Defaults to {@code true}.
     */
    boolean continueOnError() default true;

    /**
     * Returns the schedule delay in {@link #timeUnit()} units. Only used if {@link #cron()} is blank, in which case
     * this value should be a positive number.
     */
    long delay() default -1L;

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
