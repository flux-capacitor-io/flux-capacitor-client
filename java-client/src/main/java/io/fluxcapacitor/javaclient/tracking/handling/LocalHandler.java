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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on types or methods of message handlers. If the annotation is present, messages are handled by the
 * handler immediately after publication (i.e. in the publishing thread).
 * <p>
 * This is akin to invoking a method on an injected service in traditional applications, but comes with all the benefits
 * of location transparency.
 * <p>
 * Local handlers are often desirable for frequent queries or requests that require a response after the shortest
 * possible latency.
 * <p>
 * Typically, though depending on {@link MessageType} and settings of {@link LocalHandler}, a locally handled message
 * will not be logged for consumption by non-local handlers.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface LocalHandler {
    /**
     * Enables overriding the default behavior. If a type is marked as local handler, a method can still be marked as
     * non-local using annotation {@code @LocalHandler(false)}.
     */
    boolean value() default true;

    /**
     * Enables publication of locally handled messages. If {@code true}, messages and their payloads are logged as if
     * they were not handled locally. This is often desirable for locally handled queries and commands issued by e.g.
     * admins.
     */
    boolean logMessage() default false;

    /**
     * Enables publication of handler metrics, like
     * {@link io.fluxcapacitor.javaclient.tracking.metrics.HandleMessageEvent HandleMessageEvents} (if tracker
     * monitoring is enabled).
     */
    boolean logMetrics() default false;

    /**
     * Flag that indicates whether this handler will handle external (non-local) messages as well as local messages. The
     * value of this flag is ignored if {@link #value()} is false.
     */
    boolean allowExternalMessages() default false;
}
