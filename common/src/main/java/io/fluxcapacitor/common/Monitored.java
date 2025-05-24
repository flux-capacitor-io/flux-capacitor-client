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

package io.fluxcapacitor.common;

import java.util.function.Consumer;

/**
 * Represents a resource or component that can be monitored.
 * <p>
 * This interface provides a mechanism to observe activity or values of type {@code T} emitted or processed by the
 * implementing component. It is often used to monitor outgoing messages from gateways, lifecycle events, or diagnostic
 * metrics.
 *
 * <h2>Example usage</h2>
 * <pre>{@code
 * Monitored<Message> monitoredGateway = ...;
 * Registration registration = monitoredGateway.registerMonitor(message -> {
 *     log.info("Published message: {}", message);
 * });
 *
 * // Later, if needed
 * registration.cancel(); // Stops monitoring
 * }</pre>
 *
 * @param <T> the type of value being monitored (e.g., a {@code List<SerializedMessage>})
 *
 * @see Registration for managing the monitor lifecycle
 */
public interface Monitored<T> {

    /**
     * Registers a monitor that will be notified when an activity of type {@code T} occurs.
     *
     * @param monitor the callback to invoke with each observed value
     * @return a {@link Registration} that can be used to cancel the monitoring
     */
    Registration registerMonitor(Consumer<T> monitor);
}
