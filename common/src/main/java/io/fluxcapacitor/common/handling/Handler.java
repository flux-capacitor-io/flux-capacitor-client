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

import lombok.AllArgsConstructor;

import java.util.Optional;

/**
 * Represents a container for a message handler and the mechanism to resolve a {@link HandlerInvoker} for a given
 * message.
 * <p>
 * A {@code Handler} encapsulates a target class and a provider for an instance of that class. It acts as a factory for
 * {@code HandlerInvoker} instances that can be used to invoke the appropriate handler method for a given message.
 * </p>
 *
 * <p>
 * This abstraction allows support for both stateless and stateful handlers:
 * </p>
 * <ul>
 *     <li><strong>Stateless:</strong> A singleton handler instance is reused for every message (e.g., typical application service).</li>
 *     <li><strong>Stateful:</strong> The handler instance is dynamically retrieved, e.g., from a repository, based on message content (e.g., aggregates or projections).</li>
 * </ul>
 *
 * <p>
 * A handler may or may not be able to process a given message. If it can, it returns a non-empty
 * {@link Optional} containing a {@link HandlerInvoker}; otherwise, it returns {@code Optional.empty()}.
 * </p>
 *
 * <h2>Handler Architecture</h2>
 * <pre>
 * ┌────────────────────┐
 * │  HandlerInspector  │
 * └────────┬───────────┘
 *          │ inspects target class
 *          ▼
 * ┌────────────────────┐        creates        ┌──────────────────────┐
 * │  HandlerMatcher    │──────────────────────▶│     HandlerInvoker   │
 * └────────┬───────────┘                       └──────────────────────┘
 *          │ produces invoker if message
 *          │ matches a method
 *          ▼
 * ┌────────────────────┐
 * │      Handler       │◀───────────── target instance
 * └────────────────────┘
 * </pre>
 *
 * @param <M> the type of messages this handler supports (usually {@code DeserializingMessage})
 * @see HandlerInvoker
 * @see HandlerMatcher
 * @see HandlerInspector
 */
public interface Handler<M> {

    /**
     * Returns the class of the handler's target object. This may be used for reflective operations, logging, or
     * framework-level behavior.
     *
     * @return the class of the handler's target
     */
    Class<?> getTargetClass();

    /**
     * Returns a {@link HandlerInvoker} capable of processing the given message, if available.
     *
     * @param message the message to be handled
     * @return an optional {@code HandlerInvoker} if this handler can handle the message; otherwise
     * {@code Optional.empty()}
     */
    Optional<HandlerInvoker> getInvoker(M message);

    /**
     * Abstract base class for {@link Handler} implementations that delegate to another handler.
     * <p>
     * This is useful for decorating or extending handler behavior while preserving its target class and delegation
     * logic.
     * </p>
     *
     * @param <M> the message type
     */
    @AllArgsConstructor
    abstract class DelegatingHandler<M> implements Handler<M> {
        protected final Handler<M> delegate;

        @Override
        public Class<?> getTargetClass() {
            return delegate.getTargetClass();
        }
    }
}
