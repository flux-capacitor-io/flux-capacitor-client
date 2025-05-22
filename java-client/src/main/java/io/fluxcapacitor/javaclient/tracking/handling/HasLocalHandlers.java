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

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.HandlerFilter;
import io.fluxcapacitor.javaclient.tracking.TrackSelf;

import static io.fluxcapacitor.javaclient.common.ClientUtils.getLocalHandlerAnnotation;

/**
 * Base interface for gateways that support registering local message handlers.
 * <p>
 * Gateways that implement this interface can invoke message handlers directly in-memory, without involving the Flux
 * platform. This is useful for scenarios where local responsiveness is critical or when the message is
 * intended only for the current application.
 * <p>
 * Local handler registration is typically used for commands, queries, events, and other messages where in-process
 * handling is desired. Handlers are registered per-target object, and can be selectively filtered using a
 * {@link HandlerFilter}.
 *
 * @see HandlerFilter
 * @see LocalHandler
 * @see TrackSelf
 */
public interface HasLocalHandlers {

    /**
     * Registers the given handler object and includes only the methods that are annotated with a recognized handler
     * annotation (e.g., {@code @HandleCommand}, {@code @HandleQuery}, etc.).
     * <p>
     * This method uses {@link LocalHandler} to determine which methods to include. If a payload has an annotated
     * handler like {@link HandleQuery} inside its class and the class is <em>not</em> annotated with {@link TrackSelf}, the
     * handler is also considered to be local.
     *
     * @param target the object containing handler methods
     * @return a {@link Registration} which can be used to unregister the handlers
     */
    default Registration registerHandler(Object target) {
        return registerHandler(target, (t, m) -> getLocalHandlerAnnotation(t, m).isPresent());
    }

    /**
     * Indicates whether any local handlers are currently registered for this gateway.
     *
     * @return {@code true} if local handlers are present, {@code false} otherwise
     */
    boolean hasLocalHandlers();

    /**
     * Sets a custom filter to control whether a handler method is considered a local handler for the current
     * application. This is typically used internally to ensure that handlers are associated with the correct
     * application or component.
     *
     * @param selfHandlerFilter a {@link HandlerFilter} to apply to registered handlers
     */
    void setSelfHandlerFilter(HandlerFilter selfHandlerFilter);

    /**
     * Registers a handler object, including only those methods that match the provided {@link HandlerFilter}.
     * <p>
     * This method offers fine-grained control over which handler methods are registered, based on custom logic applied
     * to method annotations and/or signatures.
     *
     * @param target        the handler object containing annotated methods
     * @param handlerFilter the filter used to determine which methods should be registered
     * @return a {@link Registration} which can be used to unregister the handlers
     */
    Registration registerHandler(Object target, HandlerFilter handlerFilter);
}
