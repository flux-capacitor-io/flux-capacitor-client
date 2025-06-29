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
 * <p>
 * This annotation allows a message handler or payload to be invoked even if the current user is <strong>not</strong> authenticated.
 * It effectively overrides broader authentication requirements — for example, those set by {@link RequiresUser} on a root or parent package or class.
 * </p>
 *
 * <p>
 * {@code @NoUserRequired} is particularly useful in applications where most handlers require authentication,
 * but you want to selectively permit anonymous access to specific messages or operations (e.g., public APIs, health checks).
 * </p>
 *
 * <p>
 * Note that {@code @NoUserRequired} is not the inverse of {@link RequiresUser} — it does not forbid authenticated users,
 * nor does it prevent handling if a user is present. It simply removes the requirement for authentication in the context where it is applied.
 * </p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>{@code
 * @NoUserRequired
 * @HandleCommand
 * void handle(SignUpUser command) {
 *     // This can be invoked by anonymous users
 * }
 * }</pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface NoUserRequired {
}
