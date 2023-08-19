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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface EventStoreClient extends AutoCloseable {

    default CompletableFuture<Void> storeEvents(String aggregateId, List<SerializedMessage> events, boolean storeOnly) {
        return storeEvents(aggregateId, events, storeOnly, Guarantee.STORED);
    }

    CompletableFuture<Void> storeEvents(String aggregateId, List<SerializedMessage> events, boolean storeOnly, Guarantee guarantee);

    default AggregateEventStream<SerializedMessage> getEvents(String aggregateId) {
        return getEvents(aggregateId, -1L);
    }

    AggregateEventStream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber);

    default CompletableFuture<Void> deleteEvents(String aggregateId) {
        return deleteEvents(aggregateId, Guarantee.STORED);
    }

    CompletableFuture<Void> deleteEvents(String aggregateId, Guarantee guarantee);

    CompletableFuture<Void> updateRelationships(UpdateRelationships request);

    CompletableFuture<Void> repairRelationships(RepairRelationships request);

    default Map<String, String> getAggregatesFor(String entityId) {
        return getAggregateIds(new GetAggregateIds(entityId));
    }

    Map<String, String> getAggregateIds(GetAggregateIds request);

    default List<Relationship> getRelationships(String entityId) {
        return getRelationships(new GetRelationships(entityId));
    }

    List<Relationship> getRelationships(GetRelationships request);

    @Override
    void close();
}
