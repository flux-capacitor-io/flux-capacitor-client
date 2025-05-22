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

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Enumerates the types of messages recognized and routed by the Flux platform.
 * <p>
 * Each message type represents a semantic category of communication, such as commands, events, queries, or errors.
 * These types inform how messages are tracked, handled, and optionally responded to.
 * </p>
 *
 * <p>
 * Each {@code MessageType} has two behavioral flags:
 * </p>
 *
 * <h2>Usage Examples:</h2>
 * <ul>
 *   <li>{@code COMMAND} – a state-changing request with an optional result (e.g., CreateUser)</li>
 *   <li>{@code EVENT} – a broadcast message representing something that happened</li>
 *   <li>{@code QUERY} – a read-only request expecting a result</li>
 *   <li>{@code ERROR} – indicates failure of a prior message</li>
 *   <li>{@code WEBREQUEST} / {@code WEBRESPONSE} – HTTP/WebSocket message flows via Flux</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
public enum MessageType {

    /**
     * A request to perform an action or change state. Usually processed by a command handler and optionally followed by
     * a {@link #RESULT}.
     */
    COMMAND(true, false),

    /**
     * A record of something that occurred. Does not expect a response.
     */
    EVENT(false, false),

    /**
     * Same as events, but used in a fan-out strategy when a consumer is interested in events across all segments.
     */
    NOTIFICATION(false, false),

    /**
     * A request for data or read-only information. Typically returns a {@link #RESULT}.
     */
    QUERY(true, false),

    /**
     * The result of handling a request (e.g. command, query).
     */
    RESULT(false, true),

    /**
     * A scheduled message to be delivered at a specific time.
     */
    SCHEDULE(false, false),

    /**
     * A handler failure in response to a request message or generic system failure.
     */
    ERROR(false, false),

    /**
     * Internal system event used for diagnostics and monitoring.
     */
    METRICS(false, false),

    /**
     * A tracked web request routed to a handler via HTTP or WebSocket. Expects a {@link #WEBRESPONSE}.
     */
    WEBREQUEST(true, false),

    /**
     * A response to a {@link #WEBREQUEST}, typically an HTTP or WebSocket reply.
     */
    WEBRESPONSE(false, true),

    /**
     * A message containing an updated search document from a given collection.
     */
    DOCUMENT(false, false),

    /**
     * A message from a custom message topic.
     */
    CUSTOM(true, false);

    /**
     * Whether this message type expects a response.
     */
    private final boolean request;

    /**
     * Whether this message type represents a response to a request.
     */
    private final boolean response;
}
