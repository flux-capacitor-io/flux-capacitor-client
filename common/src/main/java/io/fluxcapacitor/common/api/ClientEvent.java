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

package io.fluxcapacitor.common.api;

/**
 * Base interface for client lifecycle events such as {@link ConnectEvent} and {@link DisconnectEvent}.
 * <p>
 * These events are published by the Flux platform to notify connected systems about client activity,
 * such as when a client connects to or disconnects from the platform.
 * <p>
 * These messages are typically published to the {@code metrics} log and can be used for auditing,
 * monitoring, or debugging distributed application behavior.
 *
 * @see ConnectEvent
 * @see DisconnectEvent
 */
public interface ClientEvent extends JsonType {

    /**
     * @return the client name (application-level identifier, e.g. "frontend-service")
     */
    String getClient();

    /**
     * @return the unique instance ID of the connecting client (e.g. "frontend-service@abcd1234")
     */
    String getClientId();

    /**
     * @return the timestamp at which this event occurred (in epoch milliseconds)
     */
    long getTimestamp();
}
