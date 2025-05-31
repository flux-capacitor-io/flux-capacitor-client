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

package io.fluxcapacitor.javaclient.publishing.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.api.publishing.SetRetentionTime;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.MessageType.METRICS;

/**
 * A {@link GatewayClient} implementation that sends serialized messages to the Flux Platform over a WebSocket
 * connection.
 * <p>
 * This client is used internally by the Flux Capacitor framework to publish messages (commands, events, queries, etc.)
 * to the platform backend in a reliable, asynchronous manner. It wraps around a WebSocket transport managed by
 * {@link AbstractWebsocketClient} and uses a low-level protocol to dispatch messages as {@link SerializedMessage}
 * objects.
 *
 * <p><strong>Usage:</strong> Users typically do not use this class directly. Instead, messages are dispatched using
 * higher-level APIs like {@link io.fluxcapacitor.javaclient.publishing.CommandGateway} or static functions in
 * {@link io.fluxcapacitor.javaclient.FluxCapacitor}.
 *
 * <p>Each {@code WebsocketGatewayClient} instance is bound to a specific {@link MessageType} and topic.
 * Metrics are optionally sent with each dispatch (enabled by default except for METRICS message gateway clients to
 * prevent infinite recursion).
 *
 * <p>Features:
 * <ul>
 *   <li>Supports append operations with configurable delivery {@link Guarantee}.</li>
 *   <li>Tracks sent messages via registered monitors for observability or auditing purposes.</li>
 *   <li>Allows retention time settings to be adjusted on the gateway (if supported).</li>
 * </ul>
 *
 * @see GatewayClient
 * @see SerializedMessage
 * @see WebSocketClient
 * @see AbstractWebsocketClient
 * @see io.fluxcapacitor.javaclient.FluxCapacitor
 */
@ClientEndpoint
public class WebsocketGatewayClient extends AbstractWebsocketClient implements GatewayClient {

    private final Set<Consumer<List<SerializedMessage>>> monitors = new CopyOnWriteArraySet<>();

    private final Metadata metricsMetadata;
    private final MessageType messageType;
    private final String topic;

    /**
     * Constructs a new WebsocketGatewayClient instance using the specified parameters. This constructor initializes the
     * client to connect to a specific WebSocket endpoint for a given message type and topic.
     * <p>
     * Metrics messages are enabled unless {@link #messageType} is {@link MessageType#METRICS}.
     *
     * @param endPointUrl the WebSocket base endpoint URI to connect to
     * @param client      the WebSocketClient instance used for configuration
     * @param type        the {@link MessageType} defining the category of messages this client handles
     * @param topic       the topic associated with the messages handled by this client if {@link MessageType} is
     *                    {@link MessageType#CUSTOM} or {@link MessageType#DOCUMENT} or {@code null} otherwise
     */
    public WebsocketGatewayClient(String endPointUrl, WebSocketClient client, MessageType type, String topic) {
        this(URI.create(endPointUrl), client, type, topic, type != METRICS);
    }

    /**
     * Constructs a new WebsocketGatewayClient instance using the specified parameters. This constructor initializes the
     * client to connect to a specific WebSocket endpoint for a given message type and topic.
     *
     * @param endPointUri the WebSocket base endpoint URI to connect to
     * @param client      the WebSocketClient instance used for configuration
     * @param type        the {@link MessageType} defining the category of messages this client handles
     * @param topic       the topic associated with the messages handled by this client if {@link MessageType} is
     *                    {@link MessageType#CUSTOM} or {@link MessageType#DOCUMENT} or {@code null} otherwise
     * @param sendMetrics a flag indicating whether metrics should be enabled for this client
     */
    public WebsocketGatewayClient(URI endPointUri, WebSocketClient client,
                                  MessageType type, String topic, boolean sendMetrics) {
        super(endPointUri, client, sendMetrics, client.getClientConfig().getGatewaySessions().get(type));
        this.topic = topic;
        this.metricsMetadata = Metadata.of("messageType", type, "topic", topic);
        this.messageType = type;
    }

    @Override
    public CompletableFuture<Void> append(Guarantee guarantee, SerializedMessage... messages) {
        try {
            return sendCommand(new Append(messageType, Arrays.asList(messages), guarantee));
        } finally {
            if (!monitors.isEmpty()) {
                monitors.forEach(m -> m.accept(Arrays.asList(messages)));
            }
        }
    }

    @Override
    public CompletableFuture<Void> setRetentionTime(Duration duration, Guarantee guarantee) {
        return sendCommand(new SetRetentionTime(duration.getSeconds(), guarantee));
    }

    @Override
    public String toString() {
        return "%s-%s%s".formatted(super.toString(), messageType, topic == null ? "" : "_" + topic);
    }

    @Override
    protected Metadata metricsMetadata() {
        return metricsMetadata;
    }

    @Override
    public Registration registerMonitor(Consumer<List<SerializedMessage>> monitor) {
        monitors.add(monitor);
        return () -> monitors.remove(monitor);
    }
}
