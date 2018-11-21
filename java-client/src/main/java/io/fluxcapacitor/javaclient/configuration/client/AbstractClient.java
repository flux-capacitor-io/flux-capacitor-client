package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.ObjectUtils.MemoizingFunction;
import io.fluxcapacitor.javaclient.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
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
