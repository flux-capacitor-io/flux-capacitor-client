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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on the payload class of a Schedule handled by {@link io.fluxcapacitor.javaclient.tracking.handling.HandleSchedule}
 * annotated methods. If this annotation is present the same payload will be rescheduled after handling using a given
 * delay in ms.
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Periodic {
    /**
     * Returns the schedule delay in milliseconds. Must be positive.
     */
    long value();

    /**
     * Returns true if this periodic schedule should be automatically started if it's not already active. Defaults to
     * {@code true}.
     */
    boolean autoStart() default true;

    /**
     * Returns the initial schedule delay in milliseconds. Only relevant when {@link #autoStart()} is true.
     */
    long initialDelay() default 0;

    /**
     * Returns true if the schedule should continue after an error. Defaults to {@code true}.
     */
    boolean continueOnError() default true;

    /**
     * Returns the id of the periodic schedule. Defaults to the fully qualified name of the schedule class.
     */
    String scheduleId() default "";
}
