package io.fluxcapacitor.javaclient.configuration;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.connection.ApplicationProperties;
import io.fluxcapacitor.javaclient.eventsourcing.EventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.InMemoryEventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.websocket.WebSocketEventStoreClient;
import io.fluxcapacitor.javaclient.gateway.GatewayClient;
import io.fluxcapacitor.javaclient.gateway.websocket.WebsocketGatewayClient;
import io.fluxcapacitor.javaclient.keyvalue.InMemoryKeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.websocket.WebsocketKeyValueClient;
import io.fluxcapacitor.javaclient.scheduling.InMemorySchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.SchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.websocket.WebsocketSchedulingClient;
import io.fluxcapacitor.javaclient.tracking.InMemoryMessageStore;
import io.fluxcapacitor.javaclient.tracking.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.websocket.WebsocketTrackingClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.javaclient.common.connection.ServiceUrlBuilder.*;

public class FluxCapacitorClient {

    private final Function<MessageType, ? extends GatewayClient> gatewayClients;
    private final Function<MessageType, ? extends TrackingClient> trackingClients;
    private final EventStoreClient eventStoreClient;
    private final SchedulingClient schedulingClient;
    private final KeyValueClient keyValueClient;

    public FluxCapacitorClient(
            Function<MessageType, ? extends GatewayClient> gatewayClients,
            Function<MessageType, ? extends TrackingClient> trackingClients,
            EventStoreClient eventStoreClient, SchedulingClient schedulingClient,
            KeyValueClient keyValueClient) {
        this.gatewayClients = memoize(gatewayClients);
        this.trackingClients = memoize(trackingClients);
        this.eventStoreClient = eventStoreClient;
        this.schedulingClient = schedulingClient;
        this.keyValueClient = keyValueClient;
    }

    public static FluxCapacitorClient usingWebSockets(ApplicationProperties properties) {
        return new FluxCapacitorClient(
                type -> new WebsocketGatewayClient(producerUrl(type, properties)),
                type -> new WebsocketTrackingClient(consumerUrl(type, properties)),
                new WebSocketEventStoreClient(eventSourcingUrl(properties)),
                new WebsocketSchedulingClient(schedulingUrl(properties)),
                new WebsocketKeyValueClient(keyValueUrl(properties)));
    }

    public static FluxCapacitorClient usingInMemory() {
        InMemorySchedulingClient schedulingClient = new InMemorySchedulingClient();
        InMemoryEventStoreClient eventStoreClient = new InMemoryEventStoreClient();
        Map<MessageType, InMemoryMessageStore> messageStores = new ConcurrentHashMap<>();
        Function<MessageType, InMemoryMessageStore> messageStoreFactory = type -> messageStores.computeIfAbsent(
                type, t -> {
                    switch (t) {
                        case EVENT:
                            return eventStoreClient;
                        case SCHEDULE:
                            return schedulingClient;
                        default:
                            return new InMemoryMessageStore();
                    }
                });
        return new FluxCapacitorClient(messageStoreFactory, messageStoreFactory, eventStoreClient,
                                       schedulingClient, new InMemoryKeyValueClient());
    }

    public GatewayClient getGatewayClient(MessageType messageType) {
        return gatewayClients.apply(messageType);
    }

    public TrackingClient getTrackingClient(MessageType messageType) {
        return trackingClients.apply(messageType);
    }

    public EventStoreClient getEventStoreClient() {
        return eventStoreClient;
    }

    public SchedulingClient getSchedulingClient() {
        return schedulingClient;
    }

    public KeyValueClient getKeyValueClient() {
        return keyValueClient;
    }
}
