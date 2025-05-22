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
 * Declares role-based access control for message handlers or payload types.
 * <p>
 * When placed on a handler method, class, or package, the {@code @RequiresAnyRole} annotation restricts invocation of
 * that handler to users possessing at least one of the specified roles. If the current user (or the user associated
 * with the incoming message) lacks a matching role, the handler is silently skipped. This allows other eligible
 * handlers to take over, enabling flexible delegation.
 * <p>
 * In contrast, if this annotation is present on the <strong>payload class</strong> itself, and the user lacks a
 * required role, message handling will be actively blocked and an {@link UnauthorizedException} is thrown.
 * <p>
 * This annotation supports meta-annotations. It can be applied to a custom annotation that expresses roles using an
 * enum or another abstraction, allowing more structured or type-safe role definitions.
 *
 * <h2>Example (on handler method):</h2>
 * <pre>{@code
 * @HandleCommand
 * @RequiresAnyRole({"admin", "editor"})
 * void handle(UpdateArticle command) {}
 * }</pre>
 *
 * <h2>Example (on payload class):</h2>
 * <pre>{@code
 * @RequiresAnyRole("admin")
 * public record DeleteAccount(String userId) {}
 * }</pre>
 *
 * <h2>Meta-annotation usage (with enum roles):</h2>
 * <pre>{@code
 * @RequiresAnyRole
 * @Target({ElementType.TYPE, ElementType.METHOD})
 * public @interface RequiresRole {
 *     Role[] value();
 * }
 * }</pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RequiresAnyRole {
    /**
     * One or more role names (case-sensitive) that grant access. At least one of the listed roles must be held by the
     * user for access to be granted.
     */
    String[] value() default {};
}
