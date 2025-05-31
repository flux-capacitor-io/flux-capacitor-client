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

package io.fluxcapacitor.javaclient.web;

/**
 * Defines constants for standard and extended HTTP request methods used by Flux Capacitor's web handling system.
 * <p>
 * This interface provides:
 * <ul>
 *   <li>String constants for standard HTTP verbs (e.g. {@code GET}, {@code POST})</li>
 *   <li>Custom pseudo-methods for WebSocket lifecycle events (e.g. {@code WS_HANDSHAKE}, {@code WS_MESSAGE})</li>
 *   <li>A wildcard constant ({@code *}) for method-agnostic routing</li>
 *   <li>A helper method to detect whether a method is a WebSocket variant</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * These constants are used throughout the web routing and handler matching infrastructure:
 * <ul>
 *   <li>In {@link WebPattern} to define method constraints</li>
 *   <li>By {@link io.fluxcapacitor.javaclient.web.WebHandlerMatcher} during dispatch resolution</li>
 *   <li>In {@link io.fluxcapacitor.javaclient.web.WebRequest} metadata to interpret incoming requests</li>
 * </ul>
 *
 * <p>The extended WebSocket methods are used internally to distinguish between phases of a WebSocket session,
 * such as handshake negotiation, message delivery, or connection closure.
 *
 * @see WebPattern
 * @see WebRequest
 * @see io.fluxcapacitor.javaclient.web.WebsocketResponseInterceptor
 */
public interface HttpRequestMethod {

    /** Matches any HTTP method (wildcard). Useful for fallback routing. */
    String ANY = "*";

    // Standard HTTP request methods
    String GET = "GET";
    String POST = "POST";
    String PUT = "PUT";
    String PATCH = "PATCH";
    String DELETE = "DELETE";
    String HEAD = "HEAD";
    String OPTIONS = "OPTIONS";
    String TRACE = "TRACE";

    // Extended WebSocket lifecycle methods (used internally by Flux)

    /** Synthetic method representing a WebSocket handshake (initial HTTP upgrade). */
    String WS_HANDSHAKE = "WS_HANDSHAKE";

    /** Represents the WebSocket session being formally opened after handshake. */
    String WS_OPEN = "WS_OPEN";

    /** Represents a message sent over an established WebSocket connection. */
    String WS_MESSAGE = "WS_MESSAGE";

    /** Represents a pong response received for a ping message. */
    String WS_PONG = "WS_PONG";

    /** Represents an explicit closure of a WebSocket session. */
    String WS_CLOSE = "WS_CLOSE";

    /**
     * Determines whether the given method string represents a WebSocket-specific method.
     *
     * @param requestMethod the method string to check
     * @return {@code true} if the method is one of the {@code WS_*} variants; {@code false} otherwise
     */
    static boolean isWebsocket(String requestMethod) {
        return switch (requestMethod) {
            case WS_MESSAGE, WS_HANDSHAKE, WS_OPEN, WS_CLOSE, WS_PONG -> true;
            case null, default -> false;
        };
    }
}
