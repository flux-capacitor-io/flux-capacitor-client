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
 * Indicates that a handler method, class, package, or payload should not be invoked or processed if the current user
 * has any of the specified roles.
 *
 * <h2>Usage</h2>
 * This annotation acts as a negative filter for message handling based on user roles.
 * <ul>
 *   <li>On a <strong>handler method</strong>, <strong>class</strong>, or <strong>package</strong>: the handler is
 *       <strong>skipped</strong> if the user has a forbidden role. This allows other handlers to take over instead.</li>
 *   <li>On a <strong>payload</strong>: an <strong>exception is thrown</strong> if the user has a forbidden role.</li>
 * </ul>
 *
 * <p>
 * This is useful in scenarios where certain roles (e.g. admins) should receive different behavior or be excluded from
 * specific processing paths.
 *
 * <h2>Example: Preventing admin users from handling a message</h2>
 * <pre>{@code
 * @ForbidsAnyRole("admin")
 * @HandleCommand
 * void handle(UserRequest request) {
 *     // This handler will only run if the user is NOT an admin
 * }
 * }</pre>
 *
 * <h2>Fallback pattern with multiple handlers</h2>
 * <pre>{@code
 * @ForbidsAnyRole("admin")
 * @HandleCommand
 * void handleAsUserOnly(MyCommand command) { ... }
 *
 * @RequiresAnyRole(value = "admin", throwIfUnauthorized = false)
 * @HandleCommand
 * void handleAsAdmin(MyCommand command) { ... }
 * }</pre>
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>Role checks are performed via the configured {@link io.fluxcapacitor.javaclient.tracking.handling.authentication.UserProvider}.</li>
 *   <li>For unauthenticated users or missing roles, this restriction does <em>not</em> apply.</li>
 *   <li>This annotation supports usage as a meta-annotation for reusable constraints.</li>
 * </ul>
 *
 * @see RequiresAnyRole
 * @see io.fluxcapacitor.javaclient.tracking.handling.authentication.User
 */
@Documented
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ForbidsAnyRole {
    /**
     * One or more roles that should be excluded. If the user has any of these roles, the handler or payload will not be
     * used.
     */
    String[] value() default {};

    /**
     * Determines whether an exception should be thrown when the authorization check fails.
     * <p>
     * If {@code true} (the default), an exception will be thrown when the user is unauthorized:
     * <ul>
     *   <li>{@link UnauthenticatedException} – if no authenticated user is present</li>
     *   <li>{@link UnauthorizedException} – if the user is present but has forbidden roles</li>
     * </ul>
     * <p>
     * If {@code false}, the annotated handler or message will be silently skipped instead.
     * This opt-out strategy is useful for conditionally invoked handlers where fallback behavior is preferred.
     * <p>
     * Defaults to {@code true}.
     */
    boolean throwIfUnauthorized() default true;
}
