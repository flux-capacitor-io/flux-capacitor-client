package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;

public interface Client {

    String name();

    String id();

    GatewayClient getGatewayClient(MessageType messageType);

    TrackingClient getTrackingClient(MessageType messageType);

    EventStoreClient getEventStoreClient();

    SchedulingClient getSchedulingClient();

    KeyValueClient getKeyValueClient();

    void shutDown();
}
