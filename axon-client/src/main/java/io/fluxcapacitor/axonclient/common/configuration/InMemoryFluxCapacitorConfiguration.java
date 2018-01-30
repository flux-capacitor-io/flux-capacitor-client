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

package io.fluxcapacitor.axonclient.common.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.configuration.client.InMemoryClientProperties;
import io.fluxcapacitor.javaclient.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.client.InMemoryEventStoreClient;
import io.fluxcapacitor.javaclient.keyvalue.client.InMemoryKeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import org.axonframework.config.Configurer;

import java.util.HashMap;
import java.util.Map;

public class InMemoryFluxCapacitorConfiguration extends AbstractFluxCapacitorConfiguration {

    public static Configurer configure(Configurer configurer, String applicationName) {
        return new InMemoryFluxCapacitorConfiguration(applicationName).configure(configurer);
    }

    private final Map<MessageType, InMemoryMessageStore> messageStores = new HashMap<>();
    private final InMemoryEventStoreClient eventStore = new InMemoryEventStoreClient();
    private final InMemoryKeyValueClient keyValueClient = new InMemoryKeyValueClient();

    public InMemoryFluxCapacitorConfiguration(String applicationName) {
        super(new InMemoryClientProperties(applicationName));
    }

    @Override
    protected TrackingClient createConsumerService(MessageType type) {
        if (type == MessageType.EVENT) {
            return eventStore;
        }
        return messageStores.computeIfAbsent(type, k -> new InMemoryMessageStore());
    }

    @Override
    protected GatewayClient createProducerService(MessageType type) {
        if (type == MessageType.EVENT) {
            return eventStore;
        }
        return messageStores.computeIfAbsent(type, k -> new InMemoryMessageStore());
    }

    @Override
    protected EventStoreClient createEventStore() {
        return eventStore;
    }

    @Override
    protected KeyValueClient createKeyValueClient() {
        return keyValueClient;
    }
}
