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
 * Indicates that a handler or message requires the presence of an authenticated user.
 * <p>
 * This annotation ensures that:
 * <ul>
 *   <li>A message handler will only be invoked if {@link io.fluxcapacitor.javaclient.tracking.handling.authentication.User#getCurrent()}
 *       is not {@code null}.</li>
 *   <li>If placed on a message payload, a {@link io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException}
 *       is thrown if the message is handled without a user context.</li>
 * </ul>
 *
 * <p>
 * This annotation is implemented as a <strong>meta-annotation</strong> over {@link RequiresAnyRole}, but without
 * specifying any explicit roles. Its presence implies that <em>any</em> user, regardless of their role, is required.
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Handler requiring an authenticated user</h3>
 * <pre>{@code
 * @HandleQuery
 * @RequiresUser
 * public UserProfile handle(GetUserProfile query) {
 *     return userService.getProfileFor(User.getCurrent());
 * }
 * }</pre>
 *
 * <h3>Payload requiring an authenticated user</h3>
 * <pre>{@code
 * @Value
 * @RequiresUser
 * public class UpdateAccountSettings {
 *     AccountId accountId;
 *     AccountSettings settings;
 * }
 * }</pre>
 * In this case, if an anonymous user attempts to invoke a command with this payload,
 * a {@code UnauthenticatedException} will be thrown.
 *
 * <h2>Notes</h2>
 * <ul>
 *   <li>This annotation can be used on types, methods, constructors, annotations, and packages.</li>
 *   <li>Like {@link RequiresAnyRole}, it is composable and inherited, so it can be placed at a high level
 *       (e.g., package or class) to apply broadly.</li>
 * </ul>
 *
 * <p>
 * This annotation may also be placed on a parent or root <strong>package</strong>, via a {@code package-info.java}
 * file. When applied in this way, the rule is inherited by all message handlers,
 * payloads, and classes within that package and its subpackages, unless explicitly overridden.
 * This allows you to enforce authentication requirements across entire modules or layers of your application.
 * </p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>{@code
 * // In com/example/secure/package-info.java
 * @RequiresUser
 * package com.example.secure;
 * }</pre>
 *
 * @see RequiresAnyRole
 * @see io.fluxcapacitor.javaclient.tracking.handling.authentication.User
 * @see io.fluxcapacitor.javaclient.tracking.handling.authentication.UnauthenticatedException
 */
@Documented
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@RequiresAnyRole
public @interface RequiresUser {
    /**
     * Determines whether an exception should be thrown when the authorization check fails.
     * <p>
     * If {@code true} (the default), an UnauthenticatedException will be thrown if no user is present.
     * <p>
     * If {@code false}, the annotated handler or message will be silently skipped instead. This opt-out strategy is
     * useful for conditionally invoked handlers where fallback behavior or a timeout is preferred.
     * <p>
     * Defaults to {@code true}.
     */
    boolean throwIfUnauthorized() default true;
}
