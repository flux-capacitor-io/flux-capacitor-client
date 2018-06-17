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

package io.fluxcapacitor.javaclient.eventsourcing.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.iterate;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebSocketEventStoreClient extends AbstractWebsocketClient implements EventStoreClient {

    private final Backlog<EventBatch> backlog;
    private final int fetchBatchSize;

    public WebSocketEventStoreClient(String endPointUrl) {
        this(URI.create(endPointUrl), 1024, 1024);
    }

    public WebSocketEventStoreClient(String endPointUrl, int backlogSize) {
        this(URI.create(endPointUrl), backlogSize, 1024);
    }

    public WebSocketEventStoreClient(URI endPointUri, int backlogSize, int fetchBatchSize) {
        super(endPointUri);
        this.backlog = new Backlog<>(this::doSend, backlogSize);
        this.fetchBatchSize = fetchBatchSize;
    }

    @Override
    public Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber,
                                 List<SerializedMessage> events) {
        return backlog.add(new EventBatch(aggregateId, domain, lastSequenceNumber, events));
    }

    private Awaitable doSend(List<EventBatch> batches) {
        sendRequest(new AppendEvents(batches));
        return Awaitable.ready();
    }

    @Override
    public Stream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        return iterate((GetEventsResult) sendRequest(new GetEvents(aggregateId, lastSequenceNumber, fetchBatchSize)),
                       r -> sendRequest(new GetEvents(aggregateId, r.getEventBatch().getLastSequenceNumber(), fetchBatchSize)),
                       r -> r.getEventBatch().getEvents().size() < fetchBatchSize)
                .flatMap(r -> r.getEventBatch().getEvents().stream());
    }

}
