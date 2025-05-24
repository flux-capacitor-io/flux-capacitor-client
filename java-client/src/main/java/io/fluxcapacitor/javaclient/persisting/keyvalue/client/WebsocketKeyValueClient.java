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
import jakarta.websocket.ClientEndpoint;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ClientEndpoint
public class WebsocketKeyValueClient extends AbstractWebsocketClient implements KeyValueClient {

    public WebsocketKeyValueClient(String endPointUrl, WebSocketClient client) {
        this(URI.create(endPointUrl), client);
    }

    public WebsocketKeyValueClient(URI endpointUri, WebSocketClient client) {
        this(endpointUri, client, true);
    }

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
