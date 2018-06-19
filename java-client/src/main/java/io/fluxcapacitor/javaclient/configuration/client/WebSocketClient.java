package io.fluxcapacitor.javaclient.configuration.client;

import io.fluxcapacitor.common.MessageType;
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
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.Value;

import java.util.function.Function;

import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.consumerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.eventSourcingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.keyValueUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.producerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.schedulingUrl;
import static java.util.UUID.randomUUID;

public class WebSocketClient extends AbstractClient {

    public static WebSocketClient newInstance(Properties properties) {
        return new WebSocketClient(
                properties.getName(), properties.getId(),
                type -> new WebsocketGatewayClient(producerUrl(type, properties)),
                type -> new WebsocketTrackingClient(consumerUrl(type, properties)),
                new WebSocketEventStoreClient(eventSourcingUrl(properties)),
                new WebsocketSchedulingClient(schedulingUrl(properties)),
                new WebsocketKeyValueClient(keyValueUrl(properties)));
    }

    private WebSocketClient(String name, String id,
                            Function<MessageType, ? extends GatewayClient> gatewayClients,
                            Function<MessageType, ? extends TrackingClient> trackingClients,
                            EventStoreClient eventStoreClient,
                            SchedulingClient schedulingClient,
                            KeyValueClient keyValueClient) {
        super(name, id, gatewayClients, trackingClients, eventStoreClient, schedulingClient, keyValueClient);
    }

    @Value
    @AllArgsConstructor
    public static class Properties {
        private final @NonNull String name;
        private final @NonNull String id;
        private final @NonNull String serviceBaseUrl;
        private final String typeFilter;

        public Properties(String name, String serviceBaseUrl) {
            this(name, randomUUID().toString(), serviceBaseUrl, null);
        }
    }
}
