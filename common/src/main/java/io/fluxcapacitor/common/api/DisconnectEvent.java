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

import lombok.Value;

/**
 * A platform-generated event that indicates a client has disconnected from the Flux platform.
 * <p>
 * Includes metadata about the reason and method of disconnection.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Detect ungraceful client shutdowns or connection drops</li>
 *   <li>Monitor client session lifecycle and availability</li>
 * </ul>
 *
 * @see ConnectEvent
 */
@Value
public class DisconnectEvent implements JsonType {

    /**
     * Logical client name that initiated the disconnection.
     */
    String client;

    /**
     * Unique client instance ID.
     */
    String clientId;

    /**
     * Internal (websocket) session ID used during the connection.
     */
    String sessionId;

    /**
     * Timestamp when the disconnection occurred.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Name of the service the client was connected to (e.g., {@code event-sourcing-endpoint}).
     */
    String service;

    /**
     * Close code indicating the reason for disconnection. Often follows WebSocket close codes or internal custom
     * codes.
     */
    int code;

    /**
     * Optional reason string providing additional detail for the disconnect.
     */
    String reason;
}
