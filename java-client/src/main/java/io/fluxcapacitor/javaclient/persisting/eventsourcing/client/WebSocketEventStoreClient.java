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
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEvents;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.api.modeling.GetAggregateIds;
import io.fluxcapacitor.common.api.modeling.GetAggregateIdsResult;
import io.fluxcapacitor.common.api.modeling.GetRelationships;
import io.fluxcapacitor.common.api.modeling.GetRelationshipsResult;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.api.modeling.RepairRelationships;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.ObjectUtils.iterate;

/**
 * WebSocket-based implementation of the {@link EventStoreClient}, enabling interaction with the Flux Platform's event
 * store via a persistent WebSocket connection.
 *
 * <p>This implementation supports:
 * <ul>
 *   <li>Storing events for event-sourced aggregates</li>
 *   <li>Efficient, paginated retrieval of aggregate event streams</li>
 *   <li>Deleting aggregate event streams</li>
 *   <li>Maintaining aggregate/entity relationships</li>
 * </ul>
 *
 * <p>The {@code fetchBatchSize} setting controls how many events are fetched per paginated request when loading
 * an aggregate's event history. This ensures efficient memory usage while still supporting large aggregates.
 *
 * <p>End users rarely interact with this client directly. Instead, they typically use higher-level abstractions
 * such as {@link io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore} or
 * {@link io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository}.
 *
 * @see EventStoreClient
 * @see io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore
 * @see io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository
 */
@ClientEndpoint
public class WebSocketEventStoreClient extends AbstractWebsocketClient implements EventStoreClient {

    private final int fetchBatchSize;

    /**
     * Creates a new {@code WebSocketEventStoreClient} with a default batch size of 8192.
     *
     * @param endPointUrl The URL to the Flux Platform event sourcing endpoint.
     * @param client      The WebSocket client instance.
     */
    public WebSocketEventStoreClient(String endPointUrl, WebSocketClient client) {
        this(URI.create(endPointUrl), 8192, client);
    }

    /**
     * Creates a new {@code WebSocketEventStoreClient} with a specified batch size.
     *
     * @param endPointUri    The URI to the event store endpoint.
     * @param fetchBatchSize Maximum number of events to retrieve per page.
     * @param client         The WebSocket client.
     */
    public WebSocketEventStoreClient(URI endPointUri, int fetchBatchSize, WebSocketClient client) {
        this(endPointUri, fetchBatchSize, client, true);
    }

    /**
     * Constructs the WebSocket client with full customization.
     *
     * @param endPointUri    URI of the event sourcing endpoint.
     * @param fetchBatchSize The size of event batches fetched from the server.
     * @param client         The WebSocket client.
     * @param sendMetrics    Whether to send metrics to the Flux Platform.
     */
    public WebSocketEventStoreClient(URI endPointUri, int fetchBatchSize, WebSocketClient client,
                                     boolean sendMetrics) {
        super(endPointUri, client, sendMetrics, client.getClientConfig().getEventSourcingSessions());
        this.fetchBatchSize = fetchBatchSize;
    }

    /**
     * Stores events for a specific aggregate, with control over store-only mode and delivery guarantee.
     */
    @Override
    public CompletableFuture<Void> storeEvents(String aggregateId, List<SerializedMessage> events, boolean storeOnly,
                                               Guarantee guarantee) {
        return sendCommand(new AppendEvents(List.of(new EventBatch(aggregateId, events, storeOnly)), guarantee));
    }

    /**
     * Retrieves events for a specific aggregate starting after a given sequence number, optionally limiting the result
     * size.
     */
    @Override
    public AggregateEventStream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber, int maxSize) {
        AtomicReference<Long> highestSequenceNumber = new AtomicReference<>();
        AtomicInteger fetchedSize = new AtomicInteger();
        GetEventsResult firstBatch = sendAndWait(new GetEvents(
                aggregateId, lastSequenceNumber, maxSize <= 0 ? fetchBatchSize : maxSize));
        Stream<SerializedMessage> eventStream = iterate(
                firstBatch,
                r -> sendAndWait(
                        new GetEvents(aggregateId, r.getLastSequenceNumber(),
                                      maxSize <= 0 ? fetchBatchSize : maxSize - fetchedSize.get())),
                r -> r.getEventBatch().getEvents().size() < fetchBatchSize)
                .flatMap(r -> {
                    if (!r.getEventBatch().isEmpty()) {
                        fetchedSize.addAndGet(r.getEventBatch().getSize());
                        highestSequenceNumber.set(r.getLastSequenceNumber());
                    }
                    return r.getEventBatch().getEvents().stream();
                });
        return new AggregateEventStream<>(eventStream, aggregateId, highestSequenceNumber::get);
    }

    /**
     * Sends a request to update the relationships of an entity or aggregate.
     */
    @Override
    public CompletableFuture<Void> updateRelationships(UpdateRelationships request) {
        return sendCommand(request);
    }

    /**
     * Sends a request to repair relationships for a specific entity.
     */
    @Override
    public CompletableFuture<Void> repairRelationships(RepairRelationships request) {
        return sendCommand(request);
    }

    /**
     * Retrieves a map of aggregate IDs associated with a given entity, using a {@link GetAggregateIds} request.
     */
    @Override
    public Map<String, String> getAggregateIds(GetAggregateIds request) {
        return this.<GetAggregateIdsResult>sendAndWait(request).getAggregateIds();
    }

    /**
     * Retrieves all relationships for a given entity, using a {@link GetRelationships} request.
     */
    @Override
    public List<Relationship> getRelationships(GetRelationships request) {
        return this.<GetRelationshipsResult>sendAndWait(request).getRelationships();
    }

    /**
     * Sends a delete command for the event stream of the specified aggregate.
     */
    @Override
    public CompletableFuture<Void> deleteEvents(String aggregateId, Guarantee guarantee) {
        return sendCommand(new DeleteEvents(aggregateId, guarantee));
    }

}
