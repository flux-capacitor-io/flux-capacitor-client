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
import io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.WebSocketEventStoreClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.KeyValueClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.client.WebsocketKeyValueClient;
import io.fluxcapacitor.javaclient.persisting.search.client.SearchClient;
import io.fluxcapacitor.javaclient.persisting.search.client.WebSocketSearchClient;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.publishing.client.WebsocketGatewayClient;
import io.fluxcapacitor.javaclient.scheduling.client.SchedulingClient;
import io.fluxcapacitor.javaclient.scheduling.client.WebsocketSchedulingClient;
import io.fluxcapacitor.javaclient.tracking.client.CachingTrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.client.WebsocketTrackingClient;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm.LZ4;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.consumerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.eventSourcingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.keyValueUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.producerUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.schedulingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.searchUrl;
import static java.util.stream.Collectors.toMap;

public class WebSocketClient extends AbstractClient {

    @Getter
    private final ClientConfig clientConfig;

    public static WebSocketClient newInstance(ClientConfig clientConfig) {
        return new WebSocketClient(clientConfig);
    }

    protected WebSocketClient(ClientConfig clientConfig) {
        super(clientConfig.getName(), clientConfig.getId());
        this.clientConfig = clientConfig;
    }

    @Override
    protected GatewayClient createGatewayClient(MessageType messageType) {
        return new WebsocketGatewayClient(producerUrl(messageType, clientConfig), this, messageType);
    }

    @Override
    protected TrackingClient createTrackingClient(MessageType messageType) {
        TrackingClientConfig trackingConfig = clientConfig.getTrackingConfigs().get(messageType);
        WebsocketTrackingClient wsClient =
                new WebsocketTrackingClient(consumerUrl(messageType, clientConfig), this, messageType);
        return trackingConfig.getCacheSize() > 0
                ? new CachingTrackingClient(wsClient, trackingConfig.getCacheSize()) : wsClient;
    }

    @Override
    protected EventStoreClient createEventStoreClient() {
        return new WebSocketEventStoreClient(eventSourcingUrl(clientConfig), this);
    }

    @Override
    protected SchedulingClient createSchedulingClient() {
        return new WebsocketSchedulingClient(schedulingUrl(clientConfig), this);
    }

    @Override
    protected KeyValueClient createKeyValueClient() {
        return new WebsocketKeyValueClient(keyValueUrl(clientConfig), this);
    }

    @Override
    protected SearchClient createSearchClient() {
        return new WebSocketSearchClient(searchUrl(clientConfig), this);
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
    public static class ClientConfig {
        @NonNull String serviceBaseUrl;
        @NonNull String name;
        @NonNull @Default String id = UUID.randomUUID().toString();
        @NonNull @Default CompressionAlgorithm compression = LZ4;
        @Default int eventSourcingSessions = 2;
        @Default int keyValueSessions = 2;
        @Default int searchSessions = 2;
        @Default Map<MessageType, Integer> gatewaySessions = defaultGatewaySessions();
        @Default Map<MessageType, TrackingClientConfig> trackingConfigs = defaultTrackingSessions();
        @Default Duration pingTimeout = Duration.ofSeconds(10);
        @Default Duration pingDelay = Duration.ofMinutes(1);
        boolean disableMetrics;
        String projectId;
        String typeFilter;

        public ClientConfig withGatewaySessions(MessageType messageType, int count) {
            HashMap<MessageType, Integer> config = new HashMap<>(gatewaySessions);
            config.put(messageType, count);
            return toBuilder().gatewaySessions(config).build();
        }

        public ClientConfig withTrackingConfig(MessageType messageType, TrackingClientConfig trackingConfig) {
            HashMap<MessageType, TrackingClientConfig> config = new HashMap<>(trackingConfigs);
            config.put(messageType, trackingConfig);
            return toBuilder().trackingConfigs(config).build();
        }

        private static Map<MessageType, Integer> defaultGatewaySessions() {
            return Arrays.stream(MessageType.values()).collect(toMap(Function.identity(), t -> 1));
        }

        private static Map<MessageType, TrackingClientConfig> defaultTrackingSessions() {
            return Arrays.stream(MessageType.values()).collect(toMap(Function.identity(), t -> t == MessageType.RESULT
                    ? TrackingClientConfig.builder().cacheSize(0).build() : TrackingClientConfig.builder().build()));
        }

    }

    @Value
    @Builder(toBuilder = true)
    public static class TrackingClientConfig {
        @Default int sessions = 1;
        @Default int cacheSize = 0;
    }
}
