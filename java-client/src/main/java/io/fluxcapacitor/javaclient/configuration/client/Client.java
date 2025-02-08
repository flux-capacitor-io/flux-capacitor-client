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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;

public interface Client {

    String name();

    String id();

    String applicationId();

    default GatewayClient getGatewayClient(MessageType messageType) {
        switch (messageType) {
            case DOCUMENT, CUSTOM -> throw new UnsupportedOperationException("Topic is required");
        }
        return getGatewayClient(messageType, null);
    }

    GatewayClient getGatewayClient(MessageType messageType, String topic);

    Registration monitorDispatch(ClientDispatchMonitor monitor, MessageType... messageTypes);

    default TrackingClient getTrackingClient(MessageType messageType) {
        switch (messageType) {
            case DOCUMENT, CUSTOM -> throw new UnsupportedOperationException("Topic is required");
        }
        return getTrackingClient(messageType, null);
    }

    TrackingClient getTrackingClient(MessageType messageType, String topic);

    EventStoreClient getEventStoreClient();

    SchedulingClient getSchedulingClient();

    KeyValueClient getKeyValueClient();

    SearchClient getSearchClient();

    void shutDown();

    Registration beforeShutdown(Runnable task);

    default Client unwrap() {
        return this;
    }
}
