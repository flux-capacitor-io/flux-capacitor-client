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

package io.fluxcapacitor.javaclient.persisting.keyvalue.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.BooleanResult;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.KeyValuePair;
import io.fluxcapacitor.common.api.keyvalue.StoreValueIfAbsent;
import io.fluxcapacitor.common.api.keyvalue.StoreValues;
import io.fluxcapacitor.common.api.keyvalue.StoreValuesAndWait;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ClientEndpoint
public class WebsocketKeyValueClient extends AbstractWebsocketClient implements KeyValueClient {

    public WebsocketKeyValueClient(String endPointUrl, WebSocketClient.ClientConfig clientConfig) {
        this(URI.create(endPointUrl), clientConfig);
    }

    public WebsocketKeyValueClient(URI endpointUri, WebSocketClient.ClientConfig clientConfig) {
        this(endpointUri, clientConfig, true);
    }

    public WebsocketKeyValueClient(URI endpointUri, WebSocketClient.ClientConfig clientConfig, boolean sendMetrics) {
        super(endpointUri, clientConfig, sendMetrics, clientConfig.getKeyValueSessions());
    }

    @Override
    public Awaitable putValue(String key, Data<byte[]> value, Guarantee guarantee) {
        return switch (guarantee) {
            case NONE, SENT -> sendCommand(new StoreValues(List.of(new KeyValuePair(key, value)), guarantee));
            default -> sendCommand(new StoreValuesAndWait(List.of(new KeyValuePair(key, value))));
        };
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
    public Awaitable deleteValue(String key, Guarantee guarantee) {
        return sendCommand(new DeleteValue(key, guarantee));
    }
}
