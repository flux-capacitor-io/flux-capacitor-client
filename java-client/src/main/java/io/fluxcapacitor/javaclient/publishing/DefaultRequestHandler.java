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
import io.fluxcapacitor.common.Registration;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;
import static java.lang.String.format;

@RequiredArgsConstructor
@Slf4j
public class DefaultRequestHandler implements RequestHandler {

    private final Client client;
    private final MessageType resultType;
    private final Duration timeout;

    private final String responseConsumerName;

    public DefaultRequestHandler(Client client, MessageType resultType) {
        this(client, resultType, Duration.ofSeconds(200), format("%s_%s", client.name(), "$request-handler"));
    }

    private final Map<Integer, CompletableFuture<SerializedMessage>> callbacks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile Registration registration;

    @Override
    public CompletableFuture<SerializedMessage> sendRequest(SerializedMessage request,
                                                            Consumer<SerializedMessage> requestSender) {
        ensureStarted();
        int requestId = nextId.getAndIncrement();
        CompletableFuture<SerializedMessage> result = new CompletableFuture<SerializedMessage>()
                .orTimeout(timeout.getSeconds(), TimeUnit.SECONDS)
                .whenComplete((m, e) -> callbacks.remove(requestId));
        callbacks.put(requestId, result);
        request.setRequestId(requestId);
        request.setSource(client.id());
        requestSender.accept(request);
        return result;
    }

    @Override
    public List<CompletableFuture<SerializedMessage>> sendRequests(List<SerializedMessage> requests,
                                                                   Consumer<List<SerializedMessage>> requestSender) {
        ensureStarted();
        List<CompletableFuture<SerializedMessage>> futures = new ArrayList<>();
        requestSender.accept(requests.stream().peek(request -> {
            int requestId = nextId.getAndIncrement();
            CompletableFuture<SerializedMessage> result = new CompletableFuture<SerializedMessage>()
                    .orTimeout(timeout.getSeconds(), TimeUnit.SECONDS)
                    .whenComplete((m, e) -> callbacks.remove(requestId));
            callbacks.put(requestId, result);
            request.setRequestId(requestId);
            request.setSource(client.id());
            futures.add(result);
        }).collect(Collectors.toList()));
        return futures;
    }

    protected void handleMessages(List<SerializedMessage> messages) {
        messages.stream().filter(m -> m.getRequestId() != null).forEach(m -> {
            CompletableFuture<SerializedMessage> future = callbacks.remove(m.getRequestId());
            if (future == null) {
                log.warn("Received response with index {} for unknown request {}", m.getIndex(), m.getRequestId());
                return;
            }
            future.complete(m);
        });
    }

    protected void ensureStarted() {
        if (started.compareAndSet(false, true)) {
            registration = start(this::handleMessages, resultType, ConsumerConfiguration.builder()
                    .name(responseConsumerName)
                    .ignoreSegment(true)
                    .clientControlledIndex(true)
                    .filterMessageTarget(true)
                    .minIndex(IndexUtils.indexFromTimestamp(
                            FluxCapacitor.currentTime().minusSeconds(2)))
                    .build(), client);
        }
    }

    @Override
    public void close() {
        waitForResults(Duration.ofSeconds(2), callbacks.values());
        if (registration != null) {
            registration.cancel();
        }
    }
}
