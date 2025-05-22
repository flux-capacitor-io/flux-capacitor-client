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
 * Marks a method or constructor as an event handler for incoming messages of type {@link MessageType#EVENT}.
 * <p>
 * This annotation is used to designate methods that should be invoked when an event message is received.
 * It can be applied to instance methods or constructors. Constructors or static methods may be used for initializing stateful
 * handlers (see {@link Stateful}) in response to creation events.
 * </p>
 *
 * <p>
 * Event handlers typically return {@code void}, as event processing is usually side-effect driven
 * and does not produce a result to be returned or published.
 * </p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @HandleEvent
 * void on(UserCreated event) {
 *     //do something with the event
 * }
 * }</pre>
 *
 * @see HandleMessage
 * @see MessageType#EVENT
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@HandleMessage(MessageType.EVENT)
public @interface HandleEvent {

    /**
     * If {@code true}, disables this handler during discovery.
     */
    boolean disabled() default false;

    /**
     * Restricts which payload types this handler may be invoked for.
     */
    Class<?>[] allowedClasses() default {};
}
