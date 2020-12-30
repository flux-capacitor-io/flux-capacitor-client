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

package io.fluxcapacitor.javaclient.persisting.eventsourcing.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryEventStoreClient extends InMemoryMessageStore implements EventStoreClient {

    private final Map<String, List<EventBatch>> domainEvents = new ConcurrentHashMap<>();

    @Override
    public Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber,
                                 List<SerializedMessage> events) {
        domainEvents.compute(aggregateId, (id, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
            }
            list.add(new EventBatch(aggregateId, domain, lastSequenceNumber, events));
            return list;
        });
        return super.send(events.toArray(new SerializedMessage[0]));
    }

    @Override
    public AggregateEventStream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        List<EventBatch> eventBatches = domainEvents.getOrDefault(aggregateId, Collections.emptyList());
        Optional<EventBatch> lastBatch = eventBatches.stream().reduce((a, b) -> b);
        return new AggregateEventStream<>(
                eventBatches.stream()
                        .filter(batch -> batch.getLastSequenceNumber() > lastSequenceNumber)
                        .flatMap(batch -> {
                            List<SerializedMessage> events = batch.getEvents();
                            if (batch.getFirstSequenceNumber() > lastSequenceNumber) {
                                return events.stream();
                            }
                            return events.stream().skip(lastSequenceNumber - batch.getFirstSequenceNumber() + 1);
                        }), aggregateId, lastBatch.map(EventBatch::getDomain).orElse(null),
                () -> lastBatch.map(EventBatch::getLastSequenceNumber).orElse(null));
    }

    @Override
    public CompletableFuture<Boolean> deleteEvents(String aggregateId) {
        return CompletableFuture.completedFuture(domainEvents.remove(aggregateId) != null);
    }

}
