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
 * Enumerates supported HTTP protocol versions for web requests and responses handled through the Flux platform.
 * <p>
 * This enum may be used for routing, logging, or adapting behavior based on the protocol version negotiated during HTTP
 * or WebSocket communication.
 * </p>
 *
 * <ul>
 *   <li>{@link #HTTP_1_1} – Standard HTTP/1.1 protocol</li>
 *   <li>{@link #HTTP_2} – Modern HTTP/2 protocol with multiplexing support</li>
 * </ul>
 *
 * <p>
 * Note: WebSocket support in most platforms is based on HTTP/1.1.
 * </p>
 */
public enum HttpVersion {
    HTTP_1_1, HTTP_2
}
