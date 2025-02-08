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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.ObjectUtils.newThreadFactory;

@Slf4j
@AllArgsConstructor
public class InMemoryMessageStore implements MessageStore {

    private final Set<Consumer<List<SerializedMessage>>> monitors = new CopyOnWriteArraySet<>();
    private final ExecutorService executor = Executors.newCachedThreadPool(newThreadFactory("InMemoryMessageStore"));
    private final AtomicLong nextIndex = new AtomicLong();
    private final ConcurrentSkipListMap<Long, SerializedMessage> messageLog = new ConcurrentSkipListMap<>();
    @Getter
    private final MessageType messageType;
    @Getter @Setter
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
        executor.shutdownNow();
    }
}
