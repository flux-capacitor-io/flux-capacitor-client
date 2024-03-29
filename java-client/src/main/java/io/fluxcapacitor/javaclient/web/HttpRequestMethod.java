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

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collection;

@Getter
@AllArgsConstructor
public enum HttpRequestMethod {
    GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS, TRACE,
    WS_HANDSHAKE(true), WS_OPEN(true), WS_MESSAGE(true), WS_CLOSE(true), WS_PONG(true);

    HttpRequestMethod() {
        this(false);
    }

    private final boolean websocket;

    @Getter
    private static final Collection<HttpRequestMethod> standardMethods
            = Arrays.stream(HttpRequestMethod.values()).filter(m -> !m.isWebsocket()).toList();
}
