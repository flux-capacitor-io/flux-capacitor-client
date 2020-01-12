package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.javaclient.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.eventsourcing.client.WebSocketEventStoreClient;
import io.fluxcapacitor.javaclient.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.keyvalue.client.WebsocketKeyValueClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.publishing.client.WebsocketGatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.WebsocketSchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.WebsocketTrackingClient;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.NonNull;
import lombok.Value;

import java.util.UUID;
import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.serialization.compression.CompressionAlgorithm.LZ4;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.consumerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.eventSourcingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.keyValueUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.producerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.schedulingUrl;

public class WebSocketClient extends AbstractClient {

    public static WebSocketClient newInstance(Properties properties) {
        return new WebSocketClient(
                properties.getName(), properties.getId(),
                type -> new WebsocketGatewayClient(producerUrl(type, properties), properties),
                type -> new WebsocketTrackingClient(consumerUrl(type, properties), properties),
                new WebSocketEventStoreClient(eventSourcingUrl(properties), properties),
                new WebsocketSchedulingClient(schedulingUrl(properties), properties),
                new WebsocketKeyValueClient(keyValueUrl(properties), properties));
    }

    private WebSocketClient(String name, String id,
                            Function<MessageType, ? extends GatewayClient> gatewayClients,
                            Function<MessageType, ? extends TrackingClient> trackingClients,
                            EventStoreClient eventStoreClient,
                            SchedulingClient schedulingClient,
                            KeyValueClient keyValueClient) {
        super(name, id, gatewayClients, trackingClients, eventStoreClient, schedulingClient, keyValueClient);
    }

    @Override
    public void shutDown() {
        super.shutDown();
        //Wait some time after closing all websocket sessions. It seems a Session.close() is not synchronous.
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }
    }

    @Value
    @Builder(toBuilder = true)
    public static class Properties {
        @NonNull String serviceBaseUrl;
        @NonNull String name;
        @NonNull @Default String id = UUID.randomUUID().toString();
        @Default CompressionAlgorithm compression = LZ4;
        String projectId;
        String typeFilter;
    }
}
