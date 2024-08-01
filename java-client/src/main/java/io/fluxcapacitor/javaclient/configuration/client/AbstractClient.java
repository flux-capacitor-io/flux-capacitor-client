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

package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MemoizingFunction;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.common.ClientUtils;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static java.util.Arrays.stream;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class AbstractClient implements Client {

    MemoizingFunction<MessageType, ? extends GatewayClient> gatewayClients = memoize(this::createGatewayClient);
    MemoizingFunction<MessageType, ? extends TrackingClient> trackingClients = memoize(this::createTrackingClient);
    @Getter(lazy = true) EventStoreClient eventStoreClient = createEventStoreClient();
    @Getter(lazy = true) SchedulingClient schedulingClient = createSchedulingClient();
    @Getter(lazy = true) KeyValueClient keyValueClient = createKeyValueClient();
    @Getter(lazy = true) SearchClient searchClient = createSearchClient();


    protected final Set<Runnable> shutdownTasks = new CopyOnWriteArraySet<>();

    protected abstract GatewayClient createGatewayClient(MessageType messageType);
    protected abstract TrackingClient createTrackingClient(MessageType messageType);
    protected abstract EventStoreClient createEventStoreClient();
    protected abstract SchedulingClient createSchedulingClient();
    protected abstract KeyValueClient createKeyValueClient();
    protected abstract SearchClient createSearchClient();

    @Override
    public GatewayClient getGatewayClient(MessageType messageType) {
        return gatewayClients.apply(messageType);
    }

    @Override
    public TrackingClient getTrackingClient(MessageType messageType) {
        return trackingClients.apply(messageType);
    }

    @Override
    public void shutDown() {
        shutdownTasks.forEach(ClientUtils::tryRun);
        MessageType[] types = MessageType.values();
        stream(types).filter(trackingClients::isCached).map(trackingClients).forEach(TrackingClient::close);
        stream(types).filter(gatewayClients::isCached).map(gatewayClients).forEach(GatewayClient::close);
        getEventStoreClient().close();
        getSchedulingClient().close();
        getKeyValueClient().close();
        getSearchClient().close();
    }

    @Override
    public Registration beforeShutdown(Runnable task) {
        shutdownTasks.add(task);
        return () -> shutdownTasks.remove(task);
    }
}
