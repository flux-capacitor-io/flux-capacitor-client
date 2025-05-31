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

package io.fluxcapacitor.javaclient.persisting.keyvalue.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.BooleanResult;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.KeyValuePair;
import io.fluxcapacitor.common.api.keyvalue.StoreValueIfAbsent;
import io.fluxcapacitor.common.api.keyvalue.StoreValues;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.persisting.keyvalue.KeyValueStore;
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * WebSocket-based implementation of the {@link KeyValueClient} interface for interacting with the Flux Platform.
 * <p>
 * This client is responsible for storing, retrieving, and deleting binary key-value data over a WebSocket connection.
 * It sends encoded requests to the platform's key-value gateway endpoint, which persists and manages the data on the
 * server-side.
 *
 * <p>Operations supported:
 * <ul>
 *     <li>{@link #putValue(String, Data, Guarantee)} - Store a value with a given guarantee.</li>
 *     <li>{@link #putValueIfAbsent(String, Data)} - Conditionally store a value only if the key is not yet present.</li>
 *     <li>{@link #getValue(String)} - Retrieve a previously stored value.</li>
 *     <li>{@link #deleteValue(String, Guarantee)} - Remove a value associated with a given key.</li>
 * </ul>
 *
 * <p>This class is typically used internally by Flux clients and not accessed directly by most applications.
 * Higher-level abstractions like {@link KeyValueStore} are preferred.
 *
 * @see KeyValueClient
 * @see KeyValueStore
 */
@ClientEndpoint
public class WebsocketKeyValueClient extends AbstractWebsocketClient implements KeyValueClient {

    /**
     * Constructs a WebsocketKeyValueClient instance using the specified endpoint URL and WebSocket client. Sending of
     * metrics is enabled by default.
     *
     * @param endPointUrl the WebSocket endpoint URL to connect to
     * @param client      the WebSocketClient instance used for managing the WebSocket connection
     */
    public WebsocketKeyValueClient(String endPointUrl, WebSocketClient client) {
        this(URI.create(endPointUrl), client);
    }

    /**
     * Constructs a WebsocketKeyValueClient instance with the provided WebSocket endpoint URI and client. Sending of
     * metrics is enabled by default.
     *
     * @param endpointUri the URI of the WebSocket endpoint to connect to
     * @param client      the WebSocketClient instance used to manage the connection
     */
    public WebsocketKeyValueClient(URI endpointUri, WebSocketClient client) {
        this(endpointUri, client, true);
    }

    /**
     * Constructs a new WebsocketKeyValueClient instance.
     *
     * @param endpointUri the URI of the WebSocket server endpoint to connect to.
     * @param client      the WebSocketClient instance used for handling the WebSocket connection.
     * @param sendMetrics a flag indicating whether to enable metrics collection for this client. If true, metrics will
     *                    be sent.
     */
    public WebsocketKeyValueClient(URI endpointUri, WebSocketClient client, boolean sendMetrics) {
        super(endpointUri, client, sendMetrics, client.getClientConfig().getKeyValueSessions());
    }

    @Override
    public CompletableFuture<Void> putValue(String key, Data<byte[]> value, Guarantee guarantee) {
        return sendCommand(new StoreValues(List.of(new KeyValuePair(key, value)), guarantee));
    }

    @Override
    public CompletableFuture<Boolean> putValueIfAbsent(String key, Data<byte[]> value) {
        return send(new StoreValueIfAbsent(new KeyValuePair(key, value)))
                .thenApply(r -> ((BooleanResult) r).isSuccess());
    }

    @Override
    public Data<byte[]> getValue(String key) {
        GetValueResult result = sendAndWait(new GetValue(key));
        return result.getValue();
    }

    @Override
    public CompletableFuture<Void> deleteValue(String key, Guarantee guarantee) {
        return sendCommand(new DeleteValue(key, guarantee));
    }
}
