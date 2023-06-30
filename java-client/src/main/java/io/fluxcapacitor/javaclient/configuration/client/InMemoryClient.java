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
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.InMemoryEventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.InMemoryKeyValueClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.InMemorySearchClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;

public class InMemoryClient extends AbstractClient {

    private static Function<MessageType, InMemoryMessageStore> messageStoreFactory(Duration messageExpiration) {
        var eventStoreClient = new InMemoryEventStoreClient(messageExpiration);
        return memoize(t -> switch (t) {
            case NOTIFICATION, EVENT -> eventStoreClient;
            case SCHEDULE -> new InMemorySchedulingClient(messageExpiration);
            default -> new InMemoryMessageStore(t, messageExpiration);
        });
    }

    public static InMemoryClient newInstance() {
        return new InMemoryClient(Duration.ofMinutes(2));
    }

    public static InMemoryClient newInstance(Duration messageExpiration) {
        return new InMemoryClient(messageExpiration);
    }

    protected InMemoryClient(Duration messageExpiration) {
        this("inMemory", ManagementFactory.getRuntimeMXBean().getName(), messageStoreFactory(messageExpiration),
             new InMemoryKeyValueClient(), new InMemorySearchClient());
    }

    protected <T extends GatewayClient & TrackingClient> InMemoryClient(String name, String id,
                                                                        Function<MessageType, T> messageStoreClients,
                                                                        KeyValueClient keyValueClient,
                                                                        SearchClient searchClient) {
        super(name, id, messageStoreClients, messageStoreClients,
              (EventStoreClient) messageStoreClients.apply(MessageType.EVENT),
              (SchedulingClient) messageStoreClients.apply(MessageType.SCHEDULE), keyValueClient, searchClient);
    }
}
