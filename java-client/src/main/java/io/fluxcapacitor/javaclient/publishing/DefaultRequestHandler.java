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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;
import static java.lang.String.format;

/**
 * Default implementation of the {@link RequestHandler} interface.
 * <p>
 * This handler supports both single and batch request dispatching, tracking responses using an internal
 * {@link java.util.concurrent.ConcurrentHashMap} keyed by {@code requestId}. When a request is sent, the handler
 * subscribes to a corresponding result log (e.g., result or web response) via a
 * {@link io.fluxcapacitor.javaclient.tracking.client.TrackingClient}, which listens for responses targeted at this
 * client only.
 *
 * <p>Each request is assigned a unique {@code requestId} and tagged with the client's {@code source} identifier.
 * When a response with a matching {@code requestId} is received, the corresponding {@link CompletableFuture} is
 * completed.
 *
 * <p>If no response is received within the configured timeout (default: 200 seconds), the future is completed
 * exceptionally.
 * <p>
 * This request handle supports chunked responses. Request senders that can deal with chunked responses should use
 * {@link #sendRequest(SerializedMessage, Consumer, Duration, Consumer)}}. If a chunked response is received, but the
 * request sender expected a single response, the intermediate responses are aggregated before completing the request.
 *
 * <p>Features:
 * <ul>
 *   <li>Supports both single and batch request dispatching.</li>
 *   <li>Tracks responses via the configured {@link MessageType} and filters using {@code filterMessageTarget = true}.</li>
 *   <li>Ensures startup of the underlying result tracker on first request dispatch.</li>
 *   <li>Cleans up subscriptions and pending futures on {@link #close()}.</li>
 * </ul>
 *
 * @see RequestHandler
 * @see MessageType#RESULT
 * @see MessageType#WEBRESPONSE
 */
@RequiredArgsConstructor
@Slf4j
public class DefaultRequestHandler implements RequestHandler {

    private final Client client;
    private final MessageType resultType;
    private final Duration timeout;
    private final String responseConsumerName;

    private final Map<Integer, ResponseCallback> callbacks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Registration registration;

    /**
     * Constructs a DefaultRequestHandler with the specified client and message type, and a default timeout of 200
     * seconds.
     *
     * @param client     the client responsible for sending and receiving messages
     * @param resultType the type of message expected as a result
     */
    public DefaultRequestHandler(Client client, MessageType resultType) {
        this(client, resultType, Duration.ofSeconds(200), format("%s_%s", client.name(), "$request-handler"));
    }

    /**
     * Sends a request and processes the response, combining intermediate responses (if any) with the final response
     * data. This method ensures intermediate results are aggregated and included in the final output.
     */
    @Override
    public CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request,
                                                            Consumer<SerializedMessage> requestSender,
                                                            Duration timeout) {
        List<SerializedMessage> intermediates = new CopyOnWriteArrayList<>();
        CompletableFuture<SerializedMessage> future = sendRequest(request, requestSender, timeout, intermediates::add);
        return future.thenApply(m -> {
            if (intermediates.isEmpty()) {
                return m;
            }
            var data = m.getData();
            byte[] allBytes = ObjectUtils.join(Stream.concat(
                    intermediates.stream().map(i -> i.data().getValue()),
                    Stream.of(data.getValue())).toArray(byte[][]::new));
            return m.withData(new Data<>(allBytes, data.getType(), data.getRevision(), data.getFormat()));
        });
    }

    @Override
    public CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request,
                                                            Consumer<SerializedMessage> requestSender,
                                                            Duration timeout,
                                                            Consumer<SerializedMessage> intermediateCallback) {
        ensureStarted();
        CompletableFuture<SerializedMessage> future = prepareRequest(request, timeout, intermediateCallback);
        requestSender.accept(request);
        return future;
    }

    @Override
    public List<CompletableFuture<SerializedMessage>> sendRequests(List<SerializedMessage> requests,
                                                                   Consumer<List<SerializedMessage>> requestSender) {
        return sendRequests(requests, requestSender, timeout);
    }

    @Override
    public List<CompletableFuture<SerializedMessage>> sendRequests(List<SerializedMessage> requests,
                                                                   Consumer<List<SerializedMessage>> requestSender,
                                                                   Duration timeout) {
        ensureStarted();
        List<CompletableFuture<SerializedMessage>> futures = new ArrayList<>();
        requestSender.accept(requests.stream().peek(request -> futures.add(prepareRequest(request, timeout, null)))
                                     .collect(Collectors.toList()));
        return futures;
    }

    protected CompletableFuture<SerializedMessage> prepareRequest(SerializedMessage request, Duration timeout,
                                                                  Consumer<SerializedMessage> intermediateCallback) {
        int requestId = nextId.getAndIncrement();
        CompletableFuture<SerializedMessage> result = new CompletableFuture<>();
        if (timeout == null) {
            timeout = this.timeout;
        }
        if (!timeout.isNegative()) {
            result = result.orTimeout(timeout.getSeconds(), TimeUnit.SECONDS);
        }
        if (intermediateCallback == null) {
            List<SerializedMessage> intermediates = new CopyOnWriteArrayList<>();
            intermediateCallback = intermediates::add;
            result = result.thenApply(m -> {
                if (intermediates.isEmpty()) {
                    return m;
                }
                var data = m.getData();
                byte[] allBytes = ObjectUtils.join(Stream.concat(
                        intermediates.stream().map(i -> i.data().getValue()),
                        Stream.of(data.getValue())).toArray(byte[][]::new));
                return m.withData(new Data<>(allBytes, data.getType(), data.getRevision(), data.getFormat()));
            });
        }
        result.whenComplete((m, e) -> callbacks.remove(requestId));
        callbacks.put(requestId, new ResponseCallback(intermediateCallback, result));
        request.setRequestId(requestId);
        request.setSource(client.id());
        return result;
    }

    protected void ensureStarted() {
        if (started.compareAndSet(false, true)) {
            registration = start(this::handleResults, resultType, ConsumerConfiguration.builder()
                    .name(responseConsumerName)
                    .ignoreSegment(true)
                    .clientControlledIndex(true)
                    .filterMessageTarget(true)
                    .minIndex(IndexUtils.indexFromTimestamp(
                            FluxCapacitor.currentTime().minusSeconds(2)))
                    .build(), client);
        }
    }

    protected void handleResults(List<SerializedMessage> messages) {
        messages.stream().filter(m -> m.getRequestId() != null).forEach(response -> {
            var callback = callbacks.remove(response.getRequestId());
            if (callback == null) {
                log.warn("Received response with index {} for unknown request {}", response.getIndex(),
                         response.getRequestId());
                return;
            }
            if (response.lastChunk()) {
                callback.finalCallback().complete(response);
            } else {
                callback.intermediateCallback().accept(response);
            }
        });
    }

    @Override
    public void close() {
        waitForResults(Duration.ofSeconds(2),
                       callbacks.values().stream().map(ResponseCallback::finalCallback).toList());
        if (registration != null) {
            registration.cancel();
        }
    }

    /**
     * Encapsulates a callback mechanism to handle both intermediate and final responses when processing requests.
     *
     * @param intermediateCallback A {@code Consumer} that processes intermediate {@code SerializedMessage} responses as
     *                             they are received.
     * @param finalCallback        A {@code CompletableFuture} that represents the final {@code SerializedMessage}
     *                             response.
     */
    protected record ResponseCallback(Consumer<SerializedMessage> intermediateCallback,
                                      CompletableFuture<SerializedMessage> finalCallback) {
    }
}
