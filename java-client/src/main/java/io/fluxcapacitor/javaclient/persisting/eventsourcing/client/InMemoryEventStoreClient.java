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
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.modeling.GetAggregateIds;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryEventStoreClient extends InMemoryMessageStore implements EventStoreClient {

    private final Map<String, List<SerializedMessage>> appliedEvents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> relationships = new ConcurrentHashMap<>();

    @Override
    public Awaitable storeEvents(String aggregateId, List<SerializedMessage> events, boolean storeOnly) {
        appliedEvents.computeIfAbsent(aggregateId, id -> new CopyOnWriteArrayList<>()).addAll(events);
        if (storeOnly) {
            return Awaitable.ready();
        }
        return super.send(Guarantee.SENT, events.toArray(new SerializedMessage[0]));
    }

    @Override
    public Awaitable updateRelationships(UpdateRelationships request) {
        request.getDissociations().forEach(r -> relationships.computeIfAbsent(
                r.getEntityId(), entityId -> new ConcurrentHashMap<>()).remove(r.getAggregateId()));
        request.getAssociations().forEach(r -> relationships.computeIfAbsent(
                r.getEntityId(), entityId -> new ConcurrentHashMap<>())
                .put(r.getAggregateId(), r.getAggregateType()));
        return Awaitable.ready();
    }

    @Override
    public AggregateEventStream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        List<SerializedMessage> events = appliedEvents.getOrDefault(aggregateId, Collections.emptyList());
        return new AggregateEventStream<>(events.subList(
                Math.min(1 + (int) lastSequenceNumber, events.size()), events.size()).stream(), aggregateId,
                () -> (long) events.size() - 1L);
    }

    @Override
    public CompletableFuture<Boolean> deleteEvents(String aggregateId) {
        return CompletableFuture.completedFuture(appliedEvents.remove(aggregateId) != null);
    }

    @Override
    public Map<String, String> getAggregateIds(GetAggregateIds request) {
        return Map.copyOf(relationships.getOrDefault(request.getEntityId(), Collections.emptyMap()));
    }
}
