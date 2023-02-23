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

package io.fluxcapacitor.javaclient.persisting.eventsourcing.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEvents;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.api.modeling.GetAggregateIds;
import io.fluxcapacitor.common.api.modeling.GetAggregateIdsResult;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.iterate;

@ClientEndpoint
public class WebSocketEventStoreClient extends AbstractWebsocketClient implements EventStoreClient {
    private final int fetchBatchSize;

    public WebSocketEventStoreClient(String endPointUrl, ClientConfig clientConfig) {
        this(URI.create(endPointUrl), 8192, clientConfig);
    }

    public WebSocketEventStoreClient(URI endPointUri, int fetchBatchSize, WebSocketClient.ClientConfig clientConfig) {
        this(endPointUri, fetchBatchSize, clientConfig, true);
    }

    public WebSocketEventStoreClient(URI endPointUri, int fetchBatchSize, WebSocketClient.ClientConfig clientConfig,
                                     boolean sendMetrics) {
        super(endPointUri, clientConfig, sendMetrics, clientConfig.getEventSourcingSessions());
        this.fetchBatchSize = fetchBatchSize;
    }

    @Override
    public Awaitable storeEvents(String aggregateId, List<SerializedMessage> events, boolean storeOnly,
                                 Guarantee guarantee) {
        return sendCommand(new AppendEvents(List.of(new EventBatch(aggregateId, events, storeOnly)), guarantee));
    }

    @Override
    public AggregateEventStream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        AtomicReference<Long> highestSequenceNumber = new AtomicReference<>();
        GetEventsResult firstBatch = sendAndWait(new GetEvents(aggregateId, lastSequenceNumber, fetchBatchSize));
        Stream<SerializedMessage> eventStream = iterate(firstBatch,
                                                        r -> sendAndWait(new GetEvents(aggregateId, r
                                                                .getLastSequenceNumber(), fetchBatchSize)),
                                                        r -> r.getEventBatch().getEvents().size() < fetchBatchSize)
                .flatMap(r -> {
                    if (!r.getEventBatch().isEmpty()) {
                        highestSequenceNumber.set(r.getLastSequenceNumber());
                    }
                    return r.getEventBatch().getEvents().stream();
                });
        return new AggregateEventStream<>(eventStream, aggregateId, highestSequenceNumber::get);
    }

    @Override
    public Awaitable updateRelationships(UpdateRelationships request) {
        return sendCommand(request);
    }

    @Override
    public Map<String, String> getAggregateIds(GetAggregateIds request) {
        return this.<GetAggregateIdsResult>sendAndWait(request).getAggregateIds();
    }

    @Override
    public Awaitable deleteEvents(String aggregateId, Guarantee guarantee) {
        return sendCommand(new DeleteEvents(aggregateId, guarantee));
    }

}
