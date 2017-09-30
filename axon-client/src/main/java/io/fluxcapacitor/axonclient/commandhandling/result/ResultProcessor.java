/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.axonclient.commandhandling.result;

import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.javaclient.tracking.ConsumerService;
import io.fluxcapacitor.javaclient.tracking.Tracking;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static io.fluxcapacitor.common.ObjectUtils.ifTrue;

@Slf4j
public class ResultProcessor implements ResultService {

    private final AxonMessageSerializer serializer;
    private final ConsumerService consumerService;
    private final String name;
    private final int threads;
    private final Map<String, CompletableFuture<Object>> outstandingRequests = new ConcurrentHashMap<>();
    private volatile Registration registration;

    public ResultProcessor(AxonMessageSerializer serializer, ConsumerService consumerService,
                           String name) {
        this(serializer, consumerService, name, 1);
    }

    public ResultProcessor(AxonMessageSerializer serializer,
                           ConsumerService consumerService, String name, int threads) {
        this.serializer = serializer;
        this.consumerService = consumerService;
        this.name = name;
        this.threads = threads;
    }

    @Override
    public CompletableFuture<Object> awaitResult(String messageId) {
        CompletableFuture<Object> result = new CompletableFuture<>();
        outstandingRequests.put(messageId, result);
        return result;
    }

    protected void handle(List<Message> batch) {
        for (Message message : batch) {
            org.axonframework.messaging.Message<?> axonMessage = serializer.deserializeMessage(message);
            String correlationId = (String) axonMessage.getMetaData().get("correlationId");
            Optional<CompletableFuture<Object>> request =
                    Optional.ofNullable(outstandingRequests.remove(correlationId));
            if (request.isPresent()) {
                CompletableFuture<Object> r = request.get();
                Object payload = axonMessage.getPayload();
                if (payload instanceof Throwable) {
                    r.completeExceptionally((Throwable) payload);
                } else {
                    r.complete(payload);
                }
                ifTrue(log.isDebugEnabled(),
                       () -> log.debug("Remaining outstanding requests: {}", outstandingRequests.size()));
            } else {
                log.warn("Received result for an unknown request {}", correlationId);
            }
        }
    }

    public void start() {
        if (registration == null) {
            registration = Tracking.start(name, threads, consumerService, this::handle);
        }
    }

    public void shutDown() {
        Optional.ofNullable(registration).ifPresent(Registration::cancel);
        registration = null;
    }
}
