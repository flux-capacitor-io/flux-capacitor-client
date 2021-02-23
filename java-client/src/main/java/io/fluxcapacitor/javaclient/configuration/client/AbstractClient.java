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

package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils.MemoizingFunction;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;

import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.util.Arrays.stream;

public abstract class AbstractClient implements Client {

    private final String name;
    private final String id;
    private final MemoizingFunction<MessageType, ? extends GatewayClient> gatewayClients;
    private final MemoizingFunction<MessageType, ? extends TrackingClient> trackingClients;
    private final EventStoreClient eventStoreClient;
    private final SchedulingClient schedulingClient;
    private final KeyValueClient keyValueClient;

    public AbstractClient(String name, String id, Function<MessageType, ? extends GatewayClient> gatewayClients,
                          Function<MessageType, ? extends TrackingClient> trackingClients,
                          EventStoreClient eventStoreClient, SchedulingClient schedulingClient,
                          KeyValueClient keyValueClient) {
        this.name = name;
        this.id = id;
        this.gatewayClients = memoize(gatewayClients);
        this.trackingClients = memoize(trackingClients);
        this.eventStoreClient = eventStoreClient;
        this.schedulingClient = schedulingClient;
        this.keyValueClient = keyValueClient;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public GatewayClient getGatewayClient(MessageType messageType) {
        return gatewayClients.apply(messageType);
    }

    @Override
    public TrackingClient getTrackingClient(MessageType messageType) {
        return trackingClients.apply(messageType);
    }

    @Override
    public EventStoreClient getEventStoreClient() {
        return eventStoreClient;
    }

    @Override
    public SchedulingClient getSchedulingClient() {
        return schedulingClient;
    }

    @Override
    public KeyValueClient getKeyValueClient() {
        return keyValueClient;
    }

    @Override
    public void shutDown() {
        MessageType[] types = MessageType.values();
        stream(types).filter(trackingClients::isCached).map(trackingClients).forEach(TrackingClient::close);
        stream(types).filter(gatewayClients::isCached).map(gatewayClients).forEach(GatewayClient::close);
        eventStoreClient.close();
        schedulingClient.close();
        keyValueClient.close();
    }
}
