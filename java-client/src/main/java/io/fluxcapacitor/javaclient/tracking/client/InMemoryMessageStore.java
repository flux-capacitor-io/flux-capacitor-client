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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.tracking.MessageStore;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;


/**
 * An in-memory implementation of the {@link MessageStore} interface for storing {@link SerializedMessage}s without
 * external persistence.
 * <p>
 * This store underpins both local tracking (via {@link LocalTrackingClient}) and local publishing (via in-memory
 * {@link GatewayClient}) in test and development environments.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Messages are assigned a unique, incrementing index upon append if none is present.</li>
 *   <li>Stored messages are retained in memory using a {@link ConcurrentSkipListMap} keyed by index.</li>
 *   <li>Supports expiration via {@link #retentionTime}, with periodic purging during appends.</li>
 *   <li>Supports message monitors that are notified after every append.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <ul>
 *   <li>Append and monitor notifications are synchronized to preserve consistency across batch inserts.</li>
 *   <li>Message storage is based on concurrent data structures, safe for multi-threaded access.</li>
 *   <li>Monitors use a {@link CopyOnWriteArraySet} for thread-safe iteration and updates.</li>
 * </ul>
 *
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Unit and integration tests for consumers, handlers, and gateways</li>
 *   <li>Simulating message flow in local environments without a Flux Capacitor backend</li>
 *   <li>Standalone tools that mock message streams</li>
 * </ul>
 *
 * <h2>Message Expiration</h2>
 * <ul>
 *   <li>Expired messages are purged based on wall-clock time via {@link FluxCapacitor#currentTime()}.</li>
 *   <li>The purge logic is triggered during each call to {@link #append(List)} when a retention policy is set.</li>
 * </ul>
 *
 * @see MessageStore
 * @see LocalTrackingClient
 */
@Slf4j
@AllArgsConstructor
public class InMemoryMessageStore implements MessageStore {

    private final Set<Consumer<List<SerializedMessage>>> monitors = new CopyOnWriteArraySet<>();
    private final AtomicLong nextIndex = new AtomicLong();
    private final ConcurrentSkipListMap<Long, SerializedMessage> messageLog = new ConcurrentSkipListMap<>();
    @Getter
    private final MessageType messageType;
    @Getter
    @Setter
    private Duration retentionTime;

    public InMemoryMessageStore(MessageType messageType) {
        this(messageType, Duration.ofMinutes(2));
    }

    @Override
    public synchronized CompletableFuture<Void> append(List<SerializedMessage> messages) {
        try {
            messages.forEach(m -> {
                if (m.getIndex() == null) {
                    m.setIndex(nextIndex.updateAndGet(IndexUtils::nextIndex));
                }
                messageLog.put(m.getIndex(), m);
            });
            if (retentionTime != null) {
                purgeExpiredMessages(retentionTime);
            }
            return CompletableFuture.completedFuture(null);
        } finally {
            notifyMonitors(messages);
        }
    }

    @Override
    public List<SerializedMessage> getBatch(Long minIndex, int maxSize, boolean inclusive) {
        ArrayList<SerializedMessage> list = new ArrayList<>(filterMessages(messageLog.tailMap(
                Optional.ofNullable(minIndex).map(i -> inclusive ? i : i + 1L).orElse(-1L)).values()));
        return list.subList(0, Math.min(maxSize, list.size()));
    }

    public void notifyMonitors() {
        notifyMonitors(Collections.emptyList());
    }

    protected synchronized void notifyMonitors(List<SerializedMessage> messages) {
        this.notifyAll();
        if (!monitors.isEmpty()) {
            monitors.forEach(m -> m.accept(messages));
        }
    }

    protected void purgeExpiredMessages(Duration messageExpiration) {
        var threshold = FluxCapacitor.currentTime().minus(messageExpiration).toEpochMilli();
        messageLog.headMap(IndexUtils.maxIndexFromMillis(threshold), true).clear();
    }

    protected Collection<SerializedMessage> filterMessages(Collection<SerializedMessage> messages) {
        return messages;
    }

    protected SerializedMessage getMessage(long index) {
        return messageLog.get(index);
    }

    @Override
    public Registration registerMonitor(Consumer<List<SerializedMessage>> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }

    @Override
    public void close() {
    }

    @Override
    public String toString() {
        return "InMemoryMessageStore{" +
               "messageType=" + messageType +
               '}';
    }
}
