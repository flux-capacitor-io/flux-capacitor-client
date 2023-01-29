/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.Arrays;

import static io.fluxcapacitor.common.MessageType.METRICS;

@ClientEndpoint
public class WebsocketGatewayClient extends AbstractWebsocketClient implements GatewayClient {

    private final Metadata metricsMetadata;

    public WebsocketGatewayClient(String endPointUrl, ClientConfig clientConfig, MessageType type) {
        this(URI.create(endPointUrl), clientConfig, type);
    }

    public WebsocketGatewayClient(URI endPointUri, ClientConfig clientConfig, MessageType type) {
        this(endPointUri, clientConfig, type, type != METRICS);
    }

    public WebsocketGatewayClient(URI endPointUri, WebSocketClient.ClientConfig clientConfig,
                                  MessageType type, boolean sendMetrics) {
        super(endPointUri, clientConfig, sendMetrics, clientConfig.getGatewaySessions().get(type));
        this.metricsMetadata = Metadata.of("messageType", type);
    }

    @Override
    public Awaitable send(Guarantee guarantee, SerializedMessage... messages) {
        return sendCommand(new Append(Arrays.asList(messages), guarantee));
    }

    @Override
    protected Metadata metricsMetadata() {
        return metricsMetadata;
    }
}
