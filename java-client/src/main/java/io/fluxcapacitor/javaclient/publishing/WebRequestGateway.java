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

package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;
import io.fluxcapacitor.javaclient.web.WebRequest;
import io.fluxcapacitor.javaclient.web.WebRequestSettings;
import io.fluxcapacitor.javaclient.web.WebResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Gateway for sending outbound web requests via Flux Capacitor’s proxy mechanism.
 * <p>
 * This gateway does not directly execute the request. Instead, it logs the request as a {@link WebRequest} message,
 * which is then picked up by the Flux platform's proxy. The proxy only executes the request if the request URL is
 * <strong>absolute</strong> (e.g., starts with {@code http://} or {@code https://}).
 * <p>
 * <strong>Benefits of this approach include:</strong>
 * <ul>
 *     <li><strong>Traceability:</strong> All outbound web traffic is traceable through Flux message logs.</li>
 *     <li><strong>Centralization:</strong> Outgoing requests are routed via a centralized proxy, simplifying firewalling and monitoring.</li>
 *     <li><strong>Proxy-level enhancements:</strong> The proxy can apply retries, circuit breakers, filtering, and other behaviors.</li>
 * </ul>
 * <p>
 * This gateway also supports local request handlers, meaning that annotated methods (e.g., {@code @HandleWebRequest}) can respond to requests directly
 * within the application if desired.
 *
 * @see WebRequest
 * @see WebResponse
 */
public interface WebRequestGateway extends HasLocalHandlers {

    /**
     * Default request settings used when none are specified explicitly.
     */
    WebRequestSettings defaultSettings = WebRequestSettings.builder().build();

    /**
     * Sends one or more web requests without waiting for a response.
     * <p>
     * Use this for fire-and-forget scenarios (e.g., async webhook posts).
     *
     * @param guarantee indicates whether to wait until the request is sent or stored
     * @param requests  the web requests to send
     * @return a future that completes once the requests have been sent or stored
     */
    CompletableFuture<Void> sendAndForget(Guarantee guarantee, WebRequest... requests);

    /**
     * Sends the given web request using default request settings and returns a future that completes with the response.
     * <p>
     * The request must have an absolute URL to be forwarded by the Flux proxy.
     *
     * @param request the web request to send
     * @return a future completed with the {@link WebResponse}
     */
    default CompletableFuture<WebResponse> send(WebRequest request) {
        return send(request, defaultSettings);
    }

    /**
     * Sends the given web request using given request settings and returns a future that completes with the response.
     * <p>
     * The request must have an absolute URL to be forwarded by the Flux proxy.
     *
     * @param request the web request to send
     * @return a future completed with the {@link WebResponse}
     */
    CompletableFuture<WebResponse> send(WebRequest request, WebRequestSettings settings);

    /**
     * Sends the given web request using default request settings and waits for the response synchronously.
     * <p>
     * This method blocks the calling thread until the request is completed or times out.
     * <p>
     * The request must have an absolute URL to be forwarded by the Flux proxy.
     *
     * @param request the web request to send
     * @return the received {@link WebResponse}
     */
    default WebResponse sendAndWait(WebRequest request) {
        return sendAndWait(request, defaultSettings);
    }

    /**
     * Sends the given web request using the specified request settings and waits for the response synchronously.
     * <p>
     * This method blocks the calling thread until the request is completed or times out.
     * <p>
     * The request must have an absolute URL to be forwarded by the Flux proxy.
     *
     * @param request  the web request to send
     * @param settings configuration settings for this request (e.g., timeouts, accepted encodings)
     * @return the received {@link WebResponse}
     */
    WebResponse sendAndWait(WebRequest request, WebRequestSettings settings);

    /**
     * Gracefully shuts down the gateway and releases any associated resources.
     */
    void close();
}
