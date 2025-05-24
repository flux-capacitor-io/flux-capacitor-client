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

package io.fluxcapacitor.common.handling;

import lombok.NonNull;

import java.lang.reflect.Executable;

/**
 * Represents a predicate used to determine whether a given method should be considered a valid message handler.
 * <p>
 * A {@code HandlerFilter} operates on the class (typically an application component) and a method, and can be used to
 * include or exclude handler methods during the handler discovery process.
 *
 * <p>
 * It supports composition via {@code and}, {@code or}, and {@code negate}, allowing developers to build complex
 * filtering logic declaratively.
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * HandlerFilter publicMethodsOnly = (type, method) -> Modifier.isPublic(method.getModifiers());
 * HandlerFilter annotatedOnly = (type, method) -> method.isAnnotationPresent(HandleCommand.class);
 * HandlerFilter combined = publicMethodsOnly.and(annotatedOnly);
 * }</pre>
 *
 * @see HandlerInspector
 */
@FunctionalInterface
public interface HandlerFilter {

    HandlerFilter ALWAYS_HANDLE = (t, e) -> true;

    /**
     * Evaluates whether the specified method on the given class should be considered a valid handler.
     *
     * @param ownerType  the class that declares the handler method
     * @param executable the candidate method to evaluate
     * @return {@code true} if the method should be included as a handler, {@code false} otherwise
     */
    boolean test(Class<?> ownerType, Executable executable);

    /**
     * Combines this filter with another using logical AND. The resulting filter passes only if both filters pass.
     *
     * @param other another {@code HandlerFilter} to combine with
     * @return a new {@code HandlerFilter} representing the conjunction of both filters
     */
    default HandlerFilter and(@NonNull HandlerFilter other) {
        return (o, e) -> test(o, e) && other.test(o, e);
    }

    /**
     * Inverts the current filter. The resulting filter passes only if the original filter does not.
     *
     * @return a new {@code HandlerFilter} representing the logical negation of this filter
     */
    default HandlerFilter negate() {
        return (o, e) -> !test(o, e);
    }

    /**
     * Combines this filter with another using logical OR. The resulting filter passes if either of the filters passes.
     *
     * @param other another {@code HandlerFilter} to combine with
     * @return a new {@code HandlerFilter} representing the disjunction of both filters
     */
    default HandlerFilter or(@NonNull HandlerFilter other) {
        return (o, e) -> test(o, e) || other.test(o, e);
    }
}
