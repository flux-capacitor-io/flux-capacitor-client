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

package io.fluxcapacitor.testserver.websocket;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEvents;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.api.modeling.GetAggregateIds;
import io.fluxcapacitor.common.api.modeling.GetAggregateIdsResult;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public class EventSourcingEndpoint extends WebsocketEndpoint {

    private final EventStoreClient eventStore;

    @Handle
    CompletableFuture<Void> handle(AppendEvents appendEvents) {
        return CompletableFuture.allOf(appendEvents.getEventBatches().stream().map(b -> eventStore
                .storeEvents(b.getAggregateId(), b.getEvents(), b.isStoreOnly(), appendEvents.getGuarantee()))
                                               .toArray(CompletableFuture[]::new));
    }

    @Handle
    CompletableFuture<Void> handle(DeleteEvents deleteEvents) {
        return eventStore.deleteEvents(deleteEvents.getAggregateId(), deleteEvents.getGuarantee());
    }

    @Handle
    GetEventsResult handle(GetEvents getEvents) {
        AggregateEventStream<SerializedMessage> stream = eventStore
                .getEvents(getEvents.getAggregateId(), getEvents.getLastSequenceNumber());
        long lastSequenceNumber = stream.getLastSequenceNumber().orElse(-1L);
        return new GetEventsResult(getEvents.getRequestId(), new EventBatch(
                getEvents.getAggregateId(), stream.collect(Collectors.toList()), false), lastSequenceNumber);
    }

    @Handle
    CompletableFuture<Void> handle(UpdateRelationships request) {
        return eventStore.updateRelationships(request);
    }

    @Handle
    GetAggregateIdsResult handle(GetAggregateIds request) {
        return new GetAggregateIdsResult(request.getRequestId(), eventStore.getAggregateIds(request));
    }
}
