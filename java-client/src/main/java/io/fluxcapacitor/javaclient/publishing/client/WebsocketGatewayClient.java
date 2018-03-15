/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketService;

import javax.websocket.ClientEndpoint;
import javax.websocket.EncodeException;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebsocketGatewayClient extends AbstractWebsocketService implements GatewayClient {

    private final Backlog<SerializedMessage> backlog;

    public WebsocketGatewayClient(String endPointUrl) {
        this(URI.create(endPointUrl), 1024);
    }

    public WebsocketGatewayClient(String endPointUrl, int backlogSize) {
        this(URI.create(endPointUrl), backlogSize);
    }

    public WebsocketGatewayClient(URI endPointUri, int backlogSize) {
        super(endPointUri);
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

    private Awaitable doSend(List<SerializedMessage> messages) throws IOException, EncodeException {
        getSession().getBasicRemote().sendObject(new Append(messages));
        return Awaitable.ready();
    }
}
