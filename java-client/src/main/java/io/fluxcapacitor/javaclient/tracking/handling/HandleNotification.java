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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or constructor as a handler for notification messages ({@link MessageType#NOTIFICATION}).
 * <p>
 * This annotation is a shorthand for defining event handlers that must receive <strong>all matching messages across all
 * segments</strong>, regardless of how messages are normally sharded or routed in Flux. It is effectively equivalent to
 * annotating an event handler with:
 * </p>
 *
 * <pre>{@code
 * @Consumer(ignoreSegment = true, clientControlledIndex = true)
 * }</pre>
 *
 * <p>
 * This is particularly useful in scenarios where:
 * </p>
 * <ul>
 *   <li>Every application instance must observe the same messages (e.g. broadcasting updates via WebSockets)</li>
 *   <li>Routing keys are not used or not relevant</li>
 *   <li>Messages represent global or system-level state changes</li>
 * </ul>
 *
 * <p>
 * Notification handlers do not participate in normal message partitioning and are not tracked using the standard consumer index mechanism.
 * </p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @HandleNotification
 * public void on(UserStatusChanged notification) {
 *     socketGateway.broadcast(notification);
 * }
 * }</pre>
 *
 * @see HandleMessage
 * @see MessageType#NOTIFICATION
 * @see io.fluxcapacitor.javaclient.tracking.Consumer#ignoreSegment()
 * @see io.fluxcapacitor.javaclient.tracking.Consumer#clientControlledIndex()
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HandleMessage(MessageType.NOTIFICATION)
public @interface HandleNotification {
    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;

    /**
     * Restricts which payload types this handler may be invoked for.
     */
    Class<?>[] allowedClasses() default {};
}
