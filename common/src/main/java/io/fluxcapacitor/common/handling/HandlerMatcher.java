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

import java.lang.reflect.Executable;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Defines the logic to determine whether a given target object can handle a message, and how to invoke it.
 * <p>
 * A {@code HandlerMatcher} is a stateless strategy that inspects a target object and a message to:
 * <ul>
 *   <li>Determine whether the target can handle the message ({@link #canHandle(Object)})</li>
 *   <li>Expose the applicable handler methods ({@link #matchingMethods(Object)})</li>
 *   <li>Return a {@link HandlerInvoker} capable of executing the handler method ({@link #getInvoker(Object, Object)})</li>
 * </ul>
 *
 * <p>
 * Unlike a {@link Handler}, a {@code HandlerMatcher} does not resolve or manage instances.
 * It simply inspects a provided target instance and message to resolve possible invocations.
 * </p>
 *
 * @param <T> the type of the handler instance
 * @param <M> the type of the message
 * @see Handler
 * @see HandlerInvoker
 */
public interface HandlerMatcher<T, M> {

    /**
     * Returns whether the given message can be handled by a handler instance of type {@code T}. This is a lightweight
     * check and may be used for fast filtering or diagnostics.
     *
     * @param message the message to check
     * @return {@code true} if the matcher may be able to produce an invoker for the given message
     */
    boolean canHandle(M message);

    /**
     * Returns a stream of methods from the target class that match the given message. Typically used for diagnostics or
     * documentation tools.
     *
     * @param message the message to match against
     * @return a stream of matching {@link Executable} handler methods
     */
    Stream<Executable> matchingMethods(M message);

    /**
     * Attempts to resolve a {@link HandlerInvoker} for the given target instance and message.
     *
     * @param target  the handler object
     * @param message the message to be handled
     * @return an optional invoker if the message is supported by the target; empty otherwise
     */
    Optional<HandlerInvoker> getInvoker(T target, M message);
}
