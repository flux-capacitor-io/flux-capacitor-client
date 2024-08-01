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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.LocalEventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.InMemoryKeyValueStore;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.InMemorySearchStore;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.LocalSchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.LocalTrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.lang.management.ManagementFactory;
import java.time.Duration;

public class InMemoryClient extends AbstractClient {

    private final Duration messageExpiration;
    private final LocalEventStoreClient eventStore;
    private final LocalSchedulingClient scheduleStore;

    @Getter(lazy = true) @Accessors(fluent = true)
    private final String id = ManagementFactory.getRuntimeMXBean().getName();

    public static InMemoryClient newInstance() {
        return new InMemoryClient(Duration.ofMinutes(2));
    }

    public static InMemoryClient newInstance(Duration messageExpiration) {
        return new InMemoryClient(messageExpiration);
    }

    protected InMemoryClient(Duration messageExpiration) {
        this.messageExpiration = messageExpiration;
        this.eventStore = new LocalEventStoreClient(messageExpiration);
        this.scheduleStore = new LocalSchedulingClient(messageExpiration);
    }

    @Override
    public String name() {
        return "inMemory";
    }

    @Override
    public String applicationId() {
        return null;
    }

    @Override
    protected GatewayClient createGatewayClient(MessageType messageType) {
        return switch (messageType) {
            case NOTIFICATION, EVENT -> eventStore;
            case SCHEDULE -> scheduleStore;
            default -> new LocalTrackingClient(messageType, messageExpiration);
        };
    }

    @Override
    protected TrackingClient createTrackingClient(MessageType messageType) {
        return (TrackingClient) getGatewayClient(messageType);
    }

    @Override
    protected EventStoreClient createEventStoreClient() {
        return (EventStoreClient) getTrackingClient(MessageType.EVENT);
    }

    @Override
    protected SchedulingClient createSchedulingClient() {
        return (SchedulingClient) getTrackingClient(MessageType.SCHEDULE);
    }

    @Override
    protected KeyValueClient createKeyValueClient() {
        return new InMemoryKeyValueStore();
    }

    @Override
    protected SearchClient createSearchClient() {
        return new InMemorySearchStore();
    }
}
