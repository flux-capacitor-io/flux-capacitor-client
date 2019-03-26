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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.TrackingStrategy;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class InMemoryMessageStore implements GatewayClient, TrackingClient {

    private final MessageType messageType;
    private final AtomicLong nextIndex = new AtomicLong();
    private final ConcurrentSkipListMap<Long, SerializedMessage> messageLog = new ConcurrentSkipListMap<>();
    private final Map<String, Long> consumerTokens = new ConcurrentHashMap<>();
    private final List<Consumer<SerializedMessage>> monitors = new CopyOnWriteArrayList<>();

    @Override
    public Awaitable send(SerializedMessage... messages) {
        Arrays.stream(messages).forEach(m -> {
            if (m.getIndex() == null) {
                m.setIndex(nextIndex.getAndIncrement());
            }
            messageLog.put(m.getIndex(), m);
            monitors.forEach(monitor -> monitor.accept(m));
        });
        synchronized (this) {
            this.notifyAll();
        }
        return Awaitable.ready();
    }

    @Override
    public MessageBatch readAndWait(String consumer, int channel, int maxSize, Duration maxTimeout, String typeFilter,
                                    boolean ignoreMessageTarget, TrackingStrategy strategy) {
        if (channel != 0) {
            return new MessageBatch(new int[]{0, 1}, Collections.emptyList(), null);
        }
        long deadline = System.currentTimeMillis() + maxTimeout.toMillis();
        synchronized (this) {
            Map<Long, SerializedMessage> tailMap = Collections.emptyMap();
            while (System.currentTimeMillis() < deadline
                    && (tailMap = messageLog.tailMap(getLastIndex(consumer), false)).isEmpty()) {
                try {
                    this.wait(deadline - System.currentTimeMillis());
                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    return new MessageBatch(new int[]{0, 1}, Collections.emptyList(), null);
                }
            }
            List<SerializedMessage> messages = new ArrayList<>(tailMap.values());
            Long lastIndex = messages.isEmpty() ? null : messages.get(messages.size() - 1).getIndex();
            if (typeFilter != null) {
                messages = messages.stream().filter(m -> m.getData().getType().matches(typeFilter)).collect(toList());
            }
            return new MessageBatch(new int[]{0, 1}, messages, lastIndex);
        }
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, int channel, int maxSize, Duration maxTimeout,
                                                String typeFilter, boolean ignoreMessageTarget,
                                                TrackingStrategy strategy) {
        return CompletableFuture.completedFuture(readAndWait(consumer, channel, maxSize, maxTimeout, typeFilter, ignoreMessageTarget, strategy));
    }

    private long getLastIndex(String consumer) {
        return consumerTokens.computeIfAbsent(consumer, k -> -1L);
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        consumerTokens.put(consumer, lastIndex);
        return Awaitable.ready();
    }

    @Override
    public Registration registerMonitor(Consumer<SerializedMessage> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }

    @Override
    public void close() {
        //no op
    }
}
