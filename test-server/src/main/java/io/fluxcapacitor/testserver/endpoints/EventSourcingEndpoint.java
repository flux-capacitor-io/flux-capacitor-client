/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.BooleanResult;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.VoidResult;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEvents;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@AllArgsConstructor
public class EventSourcingEndpoint extends WebsocketEndpoint {

    private final EventStoreClient eventStore;

    @Handle
    public VoidResult handle(AppendEvents appendEvents) throws Exception {
        List<Awaitable> results = appendEvents.getEventBatches().stream().map(b -> eventStore
                .storeEvents(b.getAggregateId(), b.getDomain(), b.getLastSequenceNumber(), b.getEvents(),
                             b.isStoreOnly())).collect(
                Collectors.toList());
        for (Awaitable awaitable : results) {
            awaitable.await();
        }
        return new VoidResult(appendEvents.getRequestId());
    }

    @Handle
    public BooleanResult handle(DeleteEvents deleteEvents) throws Exception {
        return new BooleanResult(deleteEvents.getRequestId(),
                                 eventStore.deleteEvents(deleteEvents.getAggregateId()).get());
    }

    @Handle
    public GetEventsResult handle(GetEvents getEvents) throws Exception {
        AggregateEventStream<SerializedMessage> stream = eventStore
                .getEvents(getEvents.getAggregateId(), getEvents.getLastSequenceNumber());
        return new GetEventsResult(getEvents.getRequestId(), new EventBatch(
                getEvents.getAggregateId(), stream.getDomain(), stream.getLastSequenceNumber().orElse(-1L),
                stream.collect(Collectors.toList()), false));
    }
}
