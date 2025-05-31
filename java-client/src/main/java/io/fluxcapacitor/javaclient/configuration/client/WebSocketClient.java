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
import io.fluxcapacitor.common.application.DefaultPropertySource;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static io.fluxcapacitor.common.serialization.compression.CompressionAlgorithm.LZ4;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.eventSourcingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.gatewayUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.keyValueUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.schedulingUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.searchUrl;
import static io.fluxcapacitor.javaclient.common.websocket.ServiceUrlBuilder.trackingUrl;
import static java.util.stream.Collectors.toMap;

/**
 * A {@link Client} implementation that connects to the Flux Platform using WebSocket connections.
 * <p>
 * This client enables full integration with the Flux runtime by delegating all gateway, tracking, and subsystem
 * communication to remote endpoints defined by the {@link ClientConfig}. It is typically used in production and testing
 * environments where communication with the Flux backend is required.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * WebSocketClient client = WebSocketClient.newInstance(
 *     WebSocketClient.ClientConfig.builder()
 *         .serviceBaseUrl("wss://my.flux.host")
 *         .name("my-service")
 *         .build());
 * FluxCapacitor flux = FluxCapacitor.builder().build(client);
 * }</pre>
 *
 * @see io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor#builder()
 * @see Client
 * @see LocalClient for an in-memory alternative
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class WebSocketClient extends AbstractClient {

    @Getter
    private final ClientConfig clientConfig;

    public static WebSocketClient newInstance(ClientConfig clientConfig) {
        return new WebSocketClient(clientConfig);
    }

    @Override
    public String name() {
        return clientConfig.getName();
    }

    @Override
    public String id() {
        return clientConfig.getId();
    }

    @Override
    public String applicationId() {
        return clientConfig.getApplicationId();
    }

    @Override
    protected GatewayClient createGatewayClient(MessageType messageType, String topic) {
        return new WebsocketGatewayClient(gatewayUrl(messageType, topic, clientConfig), this, messageType, topic);
    }

    @Override
    protected TrackingClient createTrackingClient(MessageType messageType, String topic) {
        TrackingClientConfig trackingConfig = clientConfig.getTrackingConfigs().get(messageType);
        WebsocketTrackingClient wsClient =
                new WebsocketTrackingClient(trackingUrl(messageType, topic, clientConfig), this, messageType, topic);
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

    /**
     * Configuration class for creating a {@link WebSocketClient}.
     * <p>
     * This configuration defines identifiers, service URLs, compression settings, and session allocations for various
     * message types. It is immutable and can be modified fluently via its {@code #toBuilder()} method.
     */
    @Value
    @Builder(toBuilder = true)
    public static class ClientConfig {

        /**
         * The base URL for all Flux Platform services, typically starting with {@code wss://}. Defaults to
         * property {@code FLUX_BASE_URL}.
         */
        @Default @NonNull String serviceBaseUrl = DefaultPropertySource.getInstance().get("FLUX_BASE_URL");

        /**
         * The name of the application. Defaults to property {@code FLUX_APPLICATION_NAME}.
         */
        @Default @NonNull String name = DefaultPropertySource.getInstance().get("FLUX_APPLICATION_NAME");

        /**
         * The application identifier. May be {@code null} if not explicitly configured. Defaults to
         * property {@code FLUX_APPLICATION_ID}.
         */
        @Default String applicationId = DefaultPropertySource.getInstance().get("FLUX_APPLICATION_ID");

        /**
         * A unique ID for the client instance. Defaults to {@code FLUX_TASK_ID} or a randomly generated UUID.
         */
        @NonNull @Default String id = DefaultPropertySource.getInstance().get("FLUX_TASK_ID", UUID.randomUUID().toString());

        /**
         * The compression algorithm used for message transmission. Defaults to {@link CompressionAlgorithm#LZ4}.
         */
        @NonNull @Default CompressionAlgorithm compression = LZ4;

        /**
         * Number of WebSocket sessions allocated for the event sourcing subsystem. Defaults to {@code 2}.
         */
        @Default int eventSourcingSessions = 2;

        /**
         * Number of WebSocket sessions allocated for the key-value store subsystem. Defaults to {@code 2}.
         */
        @Default int keyValueSessions = 2;

        /**
         * Number of WebSocket sessions allocated for the search subsystem. Defaults to {@code 2}.
         */
        @Default int searchSessions = 2;

        /**
         * Map defining how many WebSocket gateway sessions to allocate per {@link MessageType}.
         * Defaults to {@link #defaultGatewaySessions()}.
         */
        @Default Map<MessageType, Integer> gatewaySessions = defaultGatewaySessions();

        /**
         * Configuration map for tracking clients per {@link MessageType}.
         * Defaults to {@link #defaultTrackingSessions()}.
         */
        @Default Map<MessageType, TrackingClientConfig> trackingConfigs = defaultTrackingSessions();

        /**
         * How long to wait for a ping response before timing out. Defaults to {@code 5 seconds}.
         */
        @Default Duration pingTimeout = Duration.ofSeconds(5);

        /**
         * The delay between automatic ping messages. Defaults to {@code 10 seconds}.
         */
        @Default Duration pingDelay = Duration.ofSeconds(10);

        /**
         * Whether to disable sending metrics from this client.
         */
        boolean disableMetrics;

        /**
         * Optional project identifier. If set, it will be included in all communication with the platform.
         */
        @Default String projectId = DefaultPropertySource.getInstance().get("FLUX_PROJECT_ID");

        /**
         * Optional type filter that restricts the types of messages tracked by this client.
         */
        String typeFilter;

        /**
         * Returns a new {@code ClientConfig} with a modified gateway session count for the specified message type.
         */
        public ClientConfig withGatewaySessions(MessageType messageType, int count) {
            HashMap<MessageType, Integer> config = new HashMap<>(gatewaySessions);
            config.put(messageType, count);
            return toBuilder().gatewaySessions(config).build();
        }

        /**
         * Returns a new {@code ClientConfig} with a modified tracking config for the specified message type.
         */
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

    /**
     * Configuration for a tracking client assigned to a specific {@link MessageType}.
     * <p>
     * This configuration determines how many WebSocket tracking sessions to use, and whether a local message cache
     * should be enabled for more efficient retrieval.
     */
    @Value
    @Builder(toBuilder = true)
    public static class TrackingClientConfig {

        /**
         * Number of parallel tracking sessions to open for the associated message type. Each session can be used to
         * track a different consumer or topic in parallel. Defaults to 1.
         */
        @Default int sessions = 1;

        /**
         * The size of the local message cache, used to improve tracking efficiency when multiple trackers are active on
         * the same message type and topic.
         * <p>
         * When {@code cacheSize > 0}, a single central tracker will be responsible for reading the latest messages from
         * the Flux Platform for a given topic. These messages are cached locally and can be reused by other trackers,
         * significantly reducing round-trips and load on the Flux backend.
         * <p>
         * If set to 0, each tracker reads directly from the Flux Platform independently.
         * <p>
         * This setting is especially useful when many handlers are listening to the same topic concurrently.
         */
        @Default int cacheSize = 0;
    }
}
