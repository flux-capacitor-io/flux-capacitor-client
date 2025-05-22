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

import io.fluxcapacitor.javaclient.publishing.WebRequestGateway;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

import java.time.Duration;

/**
 * Configuration settings for a {@link WebRequest} sent via the {@link WebRequestGateway}.
 * <p>
 * These settings influence how the request is processed and forwarded by the Flux proxy. While currently limited to a
 * few essential fields, this class is designed for extensibility and may be expanded over time to support headers,
 * retry policies, authentication strategies, and more.
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>{@code
 * WebRequestSettings settings = WebRequestSettings.builder()
 *     .httpVersion(HttpVersion.HTTP_2)
 *     .timeout(Duration.ofSeconds(30))
 *     .consumer("google-traffic")
 *     .build();
 * }</pre>
 *
 * @see WebRequestGateway#sendAndWait(WebRequest, WebRequestSettings)
 */
@Value
@Builder(toBuilder = true)
public class WebRequestSettings {

    /**
     * HTTP version to be used for the web request (e.g., HTTP/1.1 or HTTP/2).
     */
    @Default
    HttpVersion httpVersion = HttpVersion.HTTP_1_1;

    /**
     * Maximum duration to wait for the response before timing out.
     */
    @Default
    Duration timeout = Duration.ofMinutes(1);

    /**
     * Name of the consumer responsible for handling the request, typically {@code "forward-proxy"}.
     */
    @Default
    String consumer = "forward-proxy";
}
