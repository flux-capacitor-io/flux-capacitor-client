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

package io.fluxcapacitor.javaclient.tracking.handling.authentication;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a handler method, class, package, or payload should not be invoked or processed if a user is
 * currently authenticated.
 * <p>
 * This annotation acts as a negative filter for message handling based on user presence.
 * <p>
 * Useful for restricting certain behavior to unauthenticated flows only — such as registration, public APIs, or guest
 * access paths.
 *
 * @see RequiresAnyRole
 * @see User
 */
@Documented
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ForbidsUser {

    /**
     * Determines whether an exception should be thrown when the authorization check fails.
     * <p>
     * If {@code true} (the default), an {@link UnauthorizedException} will be thrown when a user is present.
     * <p>
     * If {@code false}, the annotated handler or message will be silently skipped instead.
     * <p>
     * This opt-out strategy is useful for conditionally invoked handlers where fallback behavior is preferred.
     */
    boolean throwIfUnauthorized() default true;
}
