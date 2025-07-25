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

package io.fluxcapacitor.javaclient.persisting.eventsourcing.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.modeling.GetAggregateIds;
import io.fluxcapacitor.common.api.modeling.GetRelationships;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.api.modeling.RepairRelationships;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.util.Collections.synchronizedMap;

/**
 * An implementation of the {@link EventStoreClient} interface that provides an in-memory event storage solution. This
 * class extends {@link InMemoryMessageStore} to inherit message store functionality and provides additional
 * capabilities for storing, retrieving, updating, and managing aggregate event streams and relationships in memory.
 * <p>
 * It is designed for use cases where events and relationships are stored and maintained in the application memory,
 * which makes it lightweight but volatile. The stored data will not persist beyond the lifetime of the application
 * process and is typically used in test scenarios or for development purposes.
 */
public class InMemoryEventStore extends InMemoryMessageStore implements EventStoreClient {

    private final Map<String, List<SerializedMessage>> appliedEvents = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> relationships = new ConcurrentHashMap<>();

    public InMemoryEventStore() {
        this(Duration.ofMinutes(2));
    }

    public InMemoryEventStore(Duration messageExpiration) {
        super(EVENT, messageExpiration);
    }

    @Override
    public CompletableFuture<Void> storeEvents(String aggregateId, List<SerializedMessage> events, boolean storeOnly,
                                               Guarantee guarantee) {
        appliedEvents.computeIfAbsent(aggregateId, id -> new CopyOnWriteArrayList<>()).addAll(events);
        if (storeOnly) {
            return CompletableFuture.completedFuture(null);
        }
        return super.append(events);
    }

    @Override
    public CompletableFuture<Void> updateRelationships(UpdateRelationships request) {
        Function<Relationship, Map<String, String>> computeIfAbsent = r -> relationships.computeIfAbsent(
                r.getEntityId(), entityId -> synchronizedMap(new LinkedHashMap<>()));
        request.getDissociations().forEach(r -> computeIfAbsent.apply(r).remove(r.getAggregateId()));
        request.getAssociations().forEach(r -> computeIfAbsent.apply(r).put(r.getAggregateId(), r.getAggregateType()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> repairRelationships(RepairRelationships request) {
        relationships.values().forEach(mapping -> mapping.remove(request.getAggregateId()));
        relationships.values().removeIf(Map::isEmpty);
        request.getEntityIds().forEach(e -> relationships.computeIfAbsent(e, entityId -> synchronizedMap(
                new LinkedHashMap<>())).put(request.getAggregateId(), request.getAggregateType()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public AggregateEventStream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber, int maxSize) {
        List<SerializedMessage> allEvents = appliedEvents.getOrDefault(aggregateId, Collections.emptyList());
        var section = allEvents.subList(Math.min(1 + (int) lastSequenceNumber, allEvents.size()), allEvents.size());
        if (maxSize > 0) {
            section = section.stream().limit(maxSize).toList();
        }
        long maxSequenceNumber = lastSequenceNumber + section.size();
        return new AggregateEventStream<>(section.stream(), aggregateId, () -> maxSequenceNumber);
    }

    @Override
    public CompletableFuture<Void> deleteEvents(String aggregateId, Guarantee guarantee) {
        appliedEvents.remove(aggregateId);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Map<String, String> getAggregateIds(GetAggregateIds request) {
        return Map.copyOf(relationships.getOrDefault(request.getEntityId(), Collections.emptyMap()));
    }

    @Override
    public List<Relationship> getRelationships(GetRelationships request) {
        return relationships.getOrDefault(request.getEntityId(), Collections.emptyMap()).entrySet().stream()
                .map(e -> Relationship.builder().entityId(request.getEntityId()).aggregateId(e.getKey())
                        .aggregateType(e.getValue()).build()).toList();
    }

    @Override
    public String toString() {
        return "InMemoryEventStore";
    }
}
