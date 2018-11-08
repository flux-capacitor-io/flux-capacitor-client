package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.client.InMemoryEventStoreClient;
import io.fluxcapacitor.javaclient.keyvalue.client.InMemoryKeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class InMemoryClient extends AbstractClient {

    public static InMemoryClient newInstance() {
        InMemorySchedulingClient schedulingClient = new InMemorySchedulingClient();
        InMemoryEventStoreClient eventStoreClient = new InMemoryEventStoreClient();
        Map<MessageType, InMemoryMessageStore> messageStores = new ConcurrentHashMap<>();
        Function<MessageType, InMemoryMessageStore> messageStoreFactory = type -> messageStores.computeIfAbsent(
                type, t -> {
                    switch (t) {
                        case NOTIFICATION:
                        case EVENT:
                            return eventStoreClient;
                        case SCHEDULE:
                            return schedulingClient;
                        default:
                            return new InMemoryMessageStore(t);
                    }
                });
        return new InMemoryClient("inMemory", ManagementFactory.getRuntimeMXBean().getName(), messageStoreFactory,
                                  messageStoreFactory, eventStoreClient, schedulingClient,
                                  new InMemoryKeyValueClient());
    }

    private InMemoryClient(String name, String id,
                           Function<MessageType, ? extends GatewayClient> gatewayClients,
                           Function<MessageType, ? extends TrackingClient> trackingClients,
                           EventStoreClient eventStoreClient,
                           SchedulingClient schedulingClient,
                           KeyValueClient keyValueClient) {
        super(name, id, gatewayClients, trackingClients, eventStoreClient, schedulingClient, keyValueClient);
    }
}
