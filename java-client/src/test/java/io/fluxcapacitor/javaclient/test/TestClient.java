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

package io.fluxcapacitor.javaclient.test;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;

import java.util.Map;
import java.util.WeakHashMap;

@Slf4j
@AllArgsConstructor
public class TestClient implements Client {
    private final Map<Object, Object> spiedComponents = new WeakHashMap<>();

    @Getter
    private final Client delegate;

    @SuppressWarnings("unchecked")
    protected <T> T decorate(T component) {
        return (T) spiedComponents.computeIfAbsent(component, Mockito::spy);
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String id() {
        return delegate.id();
    }

    @Override
    public GatewayClient getGatewayClient(MessageType messageType) {
        return decorate(delegate.getGatewayClient(messageType));
    }

    @Override
    public TrackingClient getTrackingClient(MessageType messageType) {
        return decorate(delegate.getTrackingClient(messageType));
    }

    @Override
    public EventStoreClient getEventStoreClient() {
        return decorate(delegate.getEventStoreClient());
    }

    @Override
    public SchedulingClient getSchedulingClient() {
        return decorate(delegate.getSchedulingClient());
    }

    @Override
    public KeyValueClient getKeyValueClient() {
        return decorate(delegate.getKeyValueClient());
    }

    @Override
    public SearchClient getSearchClient() {
        return decorate(delegate.getSearchClient());
    }

    @Override
    public void shutDown() {
        delegate.shutDown();
    }
}
