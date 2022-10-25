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
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.KeyValuePair;
import io.fluxcapacitor.common.api.keyvalue.StoreValue;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;

import javax.websocket.ClientEndpoint;
import java.net.URI;

import static io.fluxcapacitor.common.Awaitable.fromFuture;

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
    public Awaitable storeValue(String key, Data<byte[]> value, boolean ifAbsent, Guarantee guarantee) {
        return fromFuture(send(new StoreValue(new KeyValuePair(key, value), ifAbsent, guarantee)));
    }

    @Override
    public Data<byte[]> getValue(String key) {
        return this.<GetValueResult>sendAndWait(new GetValue(key)).getValue();
    }

    @Override
    public Awaitable deleteValue(String key, Guarantee guarantee) {
        return fromFuture(send(new DeleteValue(key, guarantee)));
    }
}
