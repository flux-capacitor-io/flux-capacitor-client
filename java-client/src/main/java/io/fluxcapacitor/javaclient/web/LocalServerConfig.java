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

import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

/**
 * Configuration for the local HTTP server used to handle {@code WebRequest} messages outside of Flux Capacitor's own
 * web handling framework.
 * <p>
 * This configuration is primarily intended for users who prefer to delegate web request handling to a custom or
 * third-party locally running HTTP server (e.g., Spring MVC, Javalin, etc.) instead of the built-in Flux Capacitor web
 * stack.
 * <p>
 * <strong>Note:</strong> This feature is not commonly used and may be deprecated or removed in future versions.
 */
@Value
@Builder
public class LocalServerConfig {

    /**
     * The port number on which the local server should listen.
     * <p>
     * Required to forward web requests to the external server.
     */
    @NonNull
    Integer port;

    /**
     * Whether to ignore 404 (Not Found) responses for unmatched requests.
     * <p>
     * If {@code true}, 404s returned by the local server will be ignored by the dispatcher. Defaults to {@code true}.
     */
    @Default
    boolean ignore404 = true;
}
