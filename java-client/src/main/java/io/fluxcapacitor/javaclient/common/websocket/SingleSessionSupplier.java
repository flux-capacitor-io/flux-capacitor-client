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

package io.fluxcapacitor.javaclient.common.websocket;


import io.fluxcapacitor.common.TimingUtils;
import org.glassfish.tyrus.client.ClientManager;

import javax.websocket.Session;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class SingleSessionSupplier implements Supplier<Session> {

    private final ClientManager client;
    private final AtomicReference<Session> session = new AtomicReference<>();
    private final URI endpointUri;
    private final Object endpoint;
    private final Duration reconnectDelay;

    public SingleSessionSupplier(URI endpointUri, Object endpoint) {
        this(ClientManager.createClient(), endpointUri, endpoint, Duration.ofSeconds(1));
    }

    public SingleSessionSupplier(ClientManager client, URI endpointUri, Object endpoint,
                                 Duration reconnectDelay) {
        this.client = client;
        this.endpointUri = endpointUri;
        this.endpoint = endpoint;
        this.reconnectDelay = reconnectDelay;
    }

    @Override
    public Session get() {
        return session.updateAndGet(s -> {
            while (s == null || !s.isOpen()) {
                s = TimingUtils.retryOnFailure(() -> client.connectToServer(endpoint, endpointUri), reconnectDelay);
            }
            return s;
        });
    }
}
