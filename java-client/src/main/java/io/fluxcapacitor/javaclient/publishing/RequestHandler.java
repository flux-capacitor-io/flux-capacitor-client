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

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Handles the lifecycle of request/response interactions in a Flux Capacitor client.
 * <p>
 * A {@code RequestHandler} is responsible for sending requests—such as commands, queries, or web requests—
 * and asynchronously completing them when a corresponding response is received. Requests may be handled locally
 * or remotely via the Flux platform.
 *
 * <h2>Handling Strategies</h2>
 * <ul>
 *   <li><strong>Local Handling:</strong>
 *     If a request matches a locally registered handler, the {@code RequestHandler} may invoke it directly.
 *     The result is either returned immediately or asynchronously via a {@link CompletableFuture}.</li>
 *   <li><strong>Remote Handling:</strong>
 *     If the request is dispatched to the Flux platform, the handler listens for the corresponding response
 *     via a {@link TrackingClient} subscribed to the appropriate result log (e.g. result or web response log).
 *     Responses are correlated using a unique {@code requestId} embedded in each {@link SerializedMessage}.</li>
 * </ul>
 *
 * <p>
 * To prevent unnecessary message traffic, the underlying tracker is typically configured with:
 * <ul>
 *   <li>{@code filterMessageTarget = true}, so only messages targeted to this client are delivered.</li>
 *   <li>{@code clientControlledIndex = true}, allowing precise control over result consumption.</li>
 * </ul>
 *
 * <p>
 * A {@code RequestHandler} implementation must ensure lifecycle management, including resource cleanup
 * and deregistration when {@link #close()} is invoked.
 */
public interface RequestHandler extends AutoCloseable {

    /**
     * Sends a single request and returns a future that completes when the corresponding response is received.
     *
     * <p>The request is assigned a unique {@code requestId} and dispatched using the provided sender.
     * The returned {@link CompletableFuture} is completed with the result or failed on timeout.
     *
     * @param request       The request message to be sent.
     * @param requestSender A callback used to dispatch the request (e.g. to a gateway or transport layer).
     * @return A {@link CompletableFuture} that completes with the response message or fails on timeout.
     */
    default CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request,
                                                             Consumer<SerializedMessage> requestSender) {
        return sendRequest(request, requestSender, (Duration) null);
    }

    /**
     * Sends a single request with a custom timeout and returns a future for the corresponding response.
     *
     * @param request       The request message to be sent.
     * @param requestSender A callback used to dispatch the request.
     * @param timeout       The timeout for this request. A negative value indicates no timeout.
     * @return A {@link CompletableFuture} that completes with the response or fails on timeout.
     */
    CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request,
                                                     Consumer<SerializedMessage> requestSender,
                                                     @Nullable Duration timeout);

    /**
     * Sends multiple requests and returns a list of futures for their corresponding responses.
     *
     * <p>Each request is assigned a unique {@code requestId} and dispatched using the given sender.
     * The returned list preserves the order of the input requests.
     *
     * @param requests      The requests to send.
     * @param requestSender A callback used to dispatch the requests (e.g. batch publisher).
     * @return A list of {@link CompletableFuture} instances, one for each request.
     */
    List<CompletableFuture<SerializedMessage>> sendRequests(List<SerializedMessage> requests,
                                                            Consumer<List<SerializedMessage>> requestSender);

    /**
     * Sends multiple requests with a custom timeout and returns a list of futures for their responses.
     *
     * @param requests      The requests to send.
     * @param requestSender A callback used to dispatch the requests.
     * @param timeout       The timeout to apply per request. A negative value disables the timeout.
     * @return A list of {@link CompletableFuture} instances, one for each request.
     */
    List<CompletableFuture<SerializedMessage>> sendRequests(List<SerializedMessage> requests,
                                                            Consumer<List<SerializedMessage>> requestSender,
                                                            @Nullable Duration timeout);

    default CompletableFuture<SerializedMessage> sendRequest(
            SerializedMessage request,
            Consumer<SerializedMessage> requestSender,
            Consumer<SerializedMessage> intermediateCallback) {
        return sendRequest(request, requestSender, null, intermediateCallback);
    }

    CompletableFuture<SerializedMessage> sendRequest(
            SerializedMessage request,
            Consumer<SerializedMessage> requestSender, Duration timeout,
            Consumer<SerializedMessage> intermediateCallback);

    /**
     * Releases all resources associated with this handler.
     *
     * <p>This typically shuts down any underlying {@link TrackingClient} subscriptions,
     * and may cancel or complete any outstanding requests.
     */
    @Override
    void close();
}
