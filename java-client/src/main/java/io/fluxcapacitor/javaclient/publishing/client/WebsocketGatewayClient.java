/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.Properties;
import lombok.Getter;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

import static io.fluxcapacitor.common.MessageType.METRICS;

@ClientEndpoint
public class WebsocketGatewayClient extends AbstractWebsocketClient implements GatewayClient {

    private final Backlog<SerializedMessage> backlog;
    @Getter
    private final MessageType type;

    public WebsocketGatewayClient(String endPointUrl, Properties properties, MessageType type) {
        this(URI.create(endPointUrl), 1024, properties, type);
    }

    public WebsocketGatewayClient(String endPointUrl, int backlogSize, Properties properties, MessageType type) {
        this(URI.create(endPointUrl), backlogSize, properties, type);
    }

    public WebsocketGatewayClient(URI endPointUri, int backlogSize, Properties properties, MessageType type) {
        super(endPointUri, properties, type != METRICS);
        this.type = type;
        this.backlog = new Backlog<>(this::doSend, backlogSize);
    }

    @Override
    public Awaitable send(SerializedMessage... messages) {
        return backlog.add(messages);
    }

    @Override
    public Registration registerMonitor(Consumer<SerializedMessage> monitor) {
        return backlog.registerMonitor(messages -> messages.forEach(monitor));
    }

    private Awaitable doSend(List<SerializedMessage> messages) {
        return send(new Append(messages));
    }
}
