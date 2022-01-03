/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.client.DefaultTracker;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static io.fluxcapacitor.javaclient.common.ClientUtils.waitForResults;

@AllArgsConstructor
@Slf4j
public class DefaultRequestHandler implements RequestHandler {

    private final Serializer serializer;
    private final Client client;
    private final MessageType resultType;
    private final Map<Integer, CompletableFuture<Message>> callbacks = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final AtomicBoolean started = new AtomicBoolean();

    @Override
    public CompletableFuture<Message> sendRequest(SerializedMessage request,
                                                  Consumer<SerializedMessage> requestSender) {
        if (started.compareAndSet(false, true)) {
            DefaultTracker.start(this::handleMessages, ConsumerConfiguration.getDefault(resultType), client);
        }
        CompletableFuture<Message> result = new CompletableFuture<>();
        int requestId = nextId.getAndIncrement();
        callbacks.put(requestId, result);
        request.setRequestId(requestId);
        request.setSource(client.id());
        requestSender.accept(request);
        return result;
    }

    @Override
    public void close() {
        waitForResults(Duration.ofSeconds(2), callbacks.values());
    }

    protected void handleMessages(List<SerializedMessage> messages) {
        messages.forEach(m -> {
            try {
                CompletableFuture<Message> future = callbacks.get(m.getRequestId());
                if (future == null) {
                    log.warn("Received response with index {} for unknown request {}", m.getIndex(), m.getRequestId());
                    return;
                }
                Object result;
                try {
                    result = serializer.deserialize(m.getData());
                } catch (Exception e) {
                    log.error("Failed to deserialize result with id {}. Continuing with next result", m.getRequestId(), e);
                    future.completeExceptionally(e);
                    return;
                }
                try {
                    if (result instanceof Throwable) {
                        future.completeExceptionally((Exception) result);
                    } else {
                        future.complete(new Message(result, m.getMetadata()));
                    }
                } catch (Exception e) {
                    log.error("Failed to complete request with id {}", m.getRequestId(), e);
                }
            } finally {
                callbacks.remove(m.getRequestId());
            }
        });
    }
}
