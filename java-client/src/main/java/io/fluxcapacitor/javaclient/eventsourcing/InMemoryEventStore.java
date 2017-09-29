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

package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.javaclient.tracking.InMemoryMessageStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class InMemoryEventStore extends InMemoryMessageStore implements EventStore {

    private final Map<String, List<EventBatch>> domainEvents = new ConcurrentHashMap<>();
    private final Map<String, Snapshot> snapshots = new ConcurrentHashMap<>();

    @Override
    public Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber, List<Message> events) {
        domainEvents.compute(aggregateId, (id, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(new EventBatch(aggregateId, domain, lastSequenceNumber, events));
            return list;
        });
        return super.send(events.toArray(new Message[0]));
    }

    @Override
    public Stream<Message> getEvents(String aggregateId, long lastSequenceNumber) {
        return domainEvents.getOrDefault(aggregateId, Collections.emptyList()).stream()
                .filter(batch -> batch.getLastSequenceNumber() > lastSequenceNumber)
                .flatMap(batch -> batch.getEvents().stream());
    }

    @Override
    public void storeSnapshot(Snapshot snapshot) {
        snapshots.put(snapshot.getAggregateId(), snapshot);
    }

    @Override
    public Optional<Snapshot> getSnapshot(String aggregateId) {
        return Optional.ofNullable(snapshots.get(aggregateId));
    }

    @Override
    public void deleteSnapshot(String aggregateId) {
        snapshots.remove(aggregateId);
    }

    @Override
    public Awaitable send(Message... messages) {
        throw new UnsupportedOperationException("Use #storeEvents instead");
    }
}
