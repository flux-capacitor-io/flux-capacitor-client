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

public interface HttpRequestMethod {

    /**
     * A constant representing a wildcard HTTP request method, used to match any method in HTTP routing or
     * handling scenarios.
     */
    String ANY = "*";

    /*
        Standard HTTP request methods
     */

    String GET = "GET";
    String POST = "POST";
    String PUT = "PUT";
    String PATCH = "PATCH";
    String DELETE = "DELETE";
    String HEAD = "HEAD";
    String OPTIONS = "OPTIONS";
    String TRACE = "TRACE";

    /*
        For requests over websocket
     */
    String WS_HANDSHAKE = "WS_HANDSHAKE";
    String WS_OPEN = "WS_OPEN";
    String WS_MESSAGE = "WS_MESSAGE";
    String WS_PONG = "WS_PONG";
    String WS_CLOSE = "WS_CLOSE";

    static boolean isWebsocket(String requestMethod) {
        return switch (requestMethod) {
            case WS_MESSAGE, WS_HANDSHAKE, WS_OPEN, WS_CLOSE, WS_PONG -> true;
            case null, default -> false;
        };
    }
}
