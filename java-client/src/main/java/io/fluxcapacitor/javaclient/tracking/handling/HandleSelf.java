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

import io.fluxcapacitor.javaclient.tracking.metrics.HandleMessageEvent;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on a method of a payload class of a request, i.e. command, query or web request. Upon publication
 * of the  request (i.e. in the publishing thread), the annotated method is invoked to handle the request.
 * <p>
 * Request like these don't require any other handler, though additional handlers may still handle the request. The
 * advantage of this is that handler logic is contained within the payload class itself.
 * <p>
 * {@link HandleSelf} comes with the same advantages of local handlers.
 *
 * @see LocalHandler
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HandleSelf {
    /**
     * Enables overriding the default behavior. If a supertype has a {@link HandleSelf} method, an overriding method can
     * disable this using annotation @HandleSelf(disabled = true) or vice versa.
     */
    boolean disabled() default false;

    /**
     * If true, the result of the handler will be ignored.
     */
    boolean passive() default false;

    /**
     * Enables publication of the handled message for other consumers. If {@code true}, messages and their payloads are
     * logged as if they have not been handled yet. This is often desirable for self-handled queries and commands issued
     * by e.g. admins.
     */
    boolean logMessage() default false;

    /**
     * Enables publication of handler metrics, like a {@link HandleMessageEvent} (if tracker monitoring is enabled).
     */
    boolean logMetrics() default false;
}
