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

package io.fluxcapacitor.javaclient.modeling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods or fields that assert whether a command or query is legal, given the current state of an
 * aggregate.
 * <p>
 * Can be used in two ways:
 * <ul>
 *     <li>On methods inside a command or query class to perform legality checks directly</li>
 *     <li>On properties of a command or query class to delegate legality checks to the annotated property’s class</li>
 * </ul>
 *
 * <h2>Method-based usage</h2>
 * Annotated methods are invoked after the aggregate and its entities are loaded using
 * {@link io.fluxcapacitor.javaclient.modeling.Entity#assertLegal} or {@link io.fluxcapacitor.javaclient.modeling.Entity#assertAndApply}.
 * <p>
 * Parameters are injected automatically and may include:
 * <ul>
 *     <li>The command or query object itself</li>
 *     <li>Any matching entity from the aggregate tree (including parent or grandparent entities)</li>
 *     <li>Other framework-specific types like {@link io.fluxcapacitor.javaclient.common.Message} or {@link io.fluxcapacitor.javaclient.tracking.handling.authentication.User}</li>
 * </ul>
 * <p>
 * Note that empty entities (i.e., those with {@code null} values) are not injected unless the parameter is annotated with {@code @Nullable}.
 *
 * <h3>Example: Validate entity does not exist yet</h3>
 * <pre>{@code
 * @AssertLegal
 * void assertNew(Issue issue) {
 *     throw new IllegalCommandException("Issue already exists");
 * }
 * }</pre>
 *
 * <h3>Example: Validate entity does exist</h3>
 * <pre>{@code
 * @AssertLegal
 * void assertExists(@Nullable Issue issue) {
 *     if (issue == null) {
 *         throw new IllegalCommandException("Issue not found");
 *     }
 * }
 * }</pre>
 *
 * <h2>Property-based usage</h2>
 * When placed on a field of a command or query payload (e.g., {@code @AssertLegal UserDetails details}),
 * the framework will look for {@code @AssertLegal} methods or fields within that field’s value.
 * <p>
 * This enables modular legality checks colocated with the data they validate.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * public class UpdateUser {
 *     UserId userId;
 *
 *     @AssertLegal
 *     UserDetails details; // triggers @AssertLegal methods inside UserDetails
 * }
 * }</pre>
 *
 * <h2>Return value inspection</h2>
 * If an {@code @AssertLegal} method returns a non-null object, Flux will also inspect that return value for further
 * {@code @AssertLegal} methods or properties. This allows for deep, composable validation logic.
 *
 * <h2>Ordering</h2>
 * Multiple legality methods may be invoked. Their execution order is determined by {@link #priority()},
 * with higher values taking precedence.
 *
 * <h2>Execution timing</h2>
 * By default, checks run immediately during handler execution. You can defer them until after the handler completes
 * (after any @Apply invocations but just before aggregate updates are committed) using {@link #afterHandler()}.
 *
 * @see io.fluxcapacitor.javaclient.modeling.Entity#assertLegal
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AssertLegal {
    int HIGHEST_PRIORITY = Integer.MAX_VALUE, LOWEST_PRIORITY = Integer.MIN_VALUE, DEFAULT_PRIORITY = 0;

    /**
     * Determines the order of assertions if there are multiple annotated methods. A method with higher priority will be
     * invoked before methods with a lower priority. Use {@link #HIGHEST_PRIORITY} to ensure that the check is performed
     * first.
     */
    int priority() default DEFAULT_PRIORITY;

    /**
     * Determines if the legality check should be performed immediately (the default), or when the current handler is
     * done, i.e.: after @Apply and just before the aggregate updates are committed.
     */
    boolean afterHandler() default false;
}
