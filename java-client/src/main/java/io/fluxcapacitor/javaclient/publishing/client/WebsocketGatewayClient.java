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
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.MessageType.METRICS;

@ClientEndpoint
public class WebsocketGatewayClient extends AbstractWebsocketClient implements GatewayClient {

    private final Backlog<SerializedMessage> sendBacklog;
    private final Backlog<SerializedMessage> storeBacklog;

    public WebsocketGatewayClient(String endPointUrl, ClientConfig clientConfig, MessageType type) {
        this(URI.create(endPointUrl), 1024, clientConfig, type);
    }

    public WebsocketGatewayClient(String endPointUrl, int backlogSize, WebSocketClient.ClientConfig clientConfig, MessageType type) {
        this(URI.create(endPointUrl), backlogSize, clientConfig, type);
    }

    public WebsocketGatewayClient(URI endPointUri, int backlogSize, ClientConfig clientConfig, MessageType type) {
        this(endPointUri, backlogSize, clientConfig, type, type != METRICS);
    }

    public WebsocketGatewayClient(URI endPointUri, int backlogSize, WebSocketClient.ClientConfig clientConfig,
                                  MessageType type, boolean sendMetrics) {
        super(endPointUri, clientConfig, sendMetrics, clientConfig.getGatewaySessions().get(type));
        this.sendBacklog = new Backlog<>(this::doSend, backlogSize);
        this.storeBacklog = new Backlog<>(this::doStore, backlogSize);
    }

    @Override
    public Awaitable send(Guarantee guarantee, SerializedMessage... messages) {
        switch (guarantee) {
            case NONE:
                Awaitable ignored = sendBacklog.add(messages);
                return Awaitable.ready();
            case SENT:
                return sendBacklog.add(messages);
            case STORED:
                return storeBacklog.add(messages);
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Registration registerMonitor(Consumer<SerializedMessage> monitor) {
        return sendBacklog.registerMonitor(messages -> messages.forEach(monitor));
    }

    private Awaitable doSend(List<SerializedMessage> messages) {
        return sendAndForget(new Append(messages, Guarantee.SENT));
    }

    protected Awaitable doStore(List<SerializedMessage> messages) {
        sendAndWait(new Append(messages, Guarantee.STORED));
        return Awaitable.ready();
    }
}
