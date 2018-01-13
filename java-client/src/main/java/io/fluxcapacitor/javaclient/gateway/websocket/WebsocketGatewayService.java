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

package io.fluxcapacitor.javaclient.gateway.websocket;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.common.api.tracking.Append;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.connection.AbstractWebsocketService;
import io.fluxcapacitor.javaclient.gateway.GatewayService;

import javax.websocket.ClientEndpoint;
import javax.websocket.EncodeException;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebsocketGatewayService extends AbstractWebsocketService implements GatewayService {

    private final Backlog<Message> backlog;

    public WebsocketGatewayService(String endPointUrl) {
        this(URI.create(endPointUrl), 1024);
    }

    public WebsocketGatewayService(String endPointUrl, int backlogSize) {
        this(URI.create(endPointUrl), backlogSize);
    }

    public WebsocketGatewayService(URI endPointUri, int backlogSize) {
        super(endPointUri);
        this.backlog = new Backlog<>(this::doSend, backlogSize);
    }

    @Override
    public Awaitable send(Message... messages) {
        return backlog.add(messages);
    }

    @Override
    public Registration registerMonitor(Consumer<Message> monitor) {
        return backlog.registerMonitor(messages -> messages.forEach(monitor));
    }

    private Awaitable doSend(List<Message> messages) throws IOException, EncodeException {
        getSession().getBasicRemote().sendObject(new Append(messages));
        return Awaitable.ready();
    }
}
