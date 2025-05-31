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

package io.fluxcapacitor.javaclient.tracking.handling;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.scheduling.MessageScheduler;
import io.fluxcapacitor.javaclient.scheduling.Periodic;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or constructor as a handler for scheduled messages ({@link MessageType#SCHEDULE}).
 * <p>
 * These handlers are invoked in response to scheduled triggers, typically time-based.
 * <p>
 * Scheduled messages may originate from an explicit schedule call (see
 * {@link MessageScheduler}), or from a payload annotated with {@link Periodic}, which
 * enables automatic rescheduling after each invocation.
 *
 * <h2>Return behavior</h2>
 * A {@code @HandleSchedule} method may return a value to control rescheduling behavior:
 * <ul>
 *     <li><b>{@code null}</b>: continue scheduling using the default strategy if the schedule is {@link Periodic}.</li>
 *     <li><b>{@link java.time.Duration} or {@link java.time.Instant}</b>: overrides the next schedule delay or time.</li>
 *     <li><b>A new payload</b>: schedules the returned object as the next scheduled message. If this new payload is annotated
 *         with {@link Periodic}, the periodic settings on that payload take precedence.</li>
 *     <li>Returning a new {@link io.fluxcapacitor.javaclient.scheduling.Schedule} reschedules that message using the specified payload and deadline.</li>
 * </ul>
 * <p>
 * To cancel a periodic schedule, throw a {@link io.fluxcapacitor.javaclient.scheduling.CancelPeriodic} exception.
 *
 * <h3>Example: Dynamic continuation using payload return</h3>
 * <pre>{@code
 * @HandleSchedule
 * PollUpdates on(PollUpdates poll) {
 *     var updates = FluxCapacitor.queryAndWait(poll);
 *     // Process updates
 *     return new PollUpdates(updates.getContinuationToken());
 * }
 * }</pre>
 * If {@code PollUpdates} is annotated with {@link Periodic}, its delay or cron expression will be used for rescheduling.
 *
 * <h3>Example: Return a delay for the next schedule</h3>
 * <pre>{@code
 * @HandleSchedule
 * Duration on(HealthCheck ping) {
 *     if (!isAlive()) {
 *         throw new CancelPeriodic("Service offline");
 *     }
 *     return Duration.ofSeconds(30);
 * }
 * }</pre>
 * This will schedule the same {@code ping} message to be retried after 30 seconds.
 *
 * @see HandleMessage
 * @see MessageType#SCHEDULE
 * @see Periodic
 * @see MessageScheduler
 * @see io.fluxcapacitor.javaclient.scheduling.CancelPeriodic
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HandleMessage(MessageType.SCHEDULE)
public @interface HandleSchedule {

    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;

    /**
     * Restricts which payload types this handler may be invoked for.
     */
    Class<?>[] allowedClasses() default {};
}
