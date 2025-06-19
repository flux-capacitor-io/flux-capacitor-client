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

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;
import java.util.Optional;

/**
 * Factory interface for creating {@link Handler} instances that process {@link DeserializingMessage}s.
 * <p>
 * A {@code HandlerFactory} is responsible for:
 * <ul>
 *     <li>Inspecting the target object for handler methods (e.g. {@code @HandleCommand}, {@code @HandleEvent}, etc.)</li>
 *     <li>Applying the provided {@link HandlerFilter} to include or exclude individual methods</li>
 *     <li>Wrapping the invocation logic with any additional {@link HandlerInterceptor}s</li>
 *     <li>Returning a fully prepared {@link Handler} instance if any suitable methods are found</li>
 * </ul>
 */
public interface HandlerFactory {

    /**
     * Attempts to create a message handler for the given {@code target} object.
     * <p>
     * This method analyzes the given object (or class) to discover message-handling methods (e.g.
     * {@code @HandleCommand}, {@code @HandleQuery}, {@code @HandleEvent}, etc.) that match the provided
     * {@link HandlerFilter}. If any matching handler methods are found, a new {@link Handler} instance is constructed
     * to wrap them.
     * <p>
     * This is a central mechanism in Flux Capacitor used to support:
     * <ul>
     *     <li>Tracking handlers for stateful components</li>
     *     <li>Mutable, dynamic, or self-handling types</li>
     *     <li>In-memory {@code @LocalHandler}s</li>
     * </ul>
     *
     * @param target            The handler target object or class. Can be a class (e.g. {@code MyHandler.class}) or an
     *                          instantiated object.
     * @param handlerFilter     A filter to determine which methods are valid handler methods. Only methods that pass
     *                          this filter are included.
     * @param extraInterceptors A list of additional {@link HandlerInterceptor}s to apply around message dispatch. These
     *                          can be used to customize behavior with logging, retry logic, etc.
     * @return An {@link Optional} containing a {@link Handler} if any suitable methods were found; otherwise, an empty
     * {@code Optional}.
     */
    Optional<Handler<DeserializingMessage>> createHandler(
            Object target, HandlerFilter handlerFilter, List<HandlerInterceptor> extraInterceptors);
}
