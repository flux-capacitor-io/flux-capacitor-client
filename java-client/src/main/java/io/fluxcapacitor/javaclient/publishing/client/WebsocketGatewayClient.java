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

@ClientEndpoint
public class WebsocketGatewayClient extends AbstractWebsocketClient implements GatewayClient {

    private final Set<Consumer<List<SerializedMessage>>> monitors = new CopyOnWriteArraySet<>();

    private final Metadata metricsMetadata;
    private final MessageType messageType;
    private final String topic;

    public WebsocketGatewayClient(String endPointUrl, WebSocketClient client, MessageType type, String topic) {
        this(URI.create(endPointUrl), client, type, topic, type != METRICS);
    }

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
