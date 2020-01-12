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

package io.fluxcapacitor.javaclient.keyvalue.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.KeyValuePair;
import io.fluxcapacitor.common.api.keyvalue.StoreValues;
import io.fluxcapacitor.common.api.keyvalue.StoreValuesAndWait;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.Properties;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;

import static java.util.Collections.singletonList;

@Slf4j
@ClientEndpoint
public class WebsocketKeyValueClient extends AbstractWebsocketClient implements KeyValueClient {

    private final Backlog<KeyValuePair> backlog;

    public WebsocketKeyValueClient(String endPointUrl, Properties properties) {
        this(URI.create(endPointUrl), properties);
    }

    public WebsocketKeyValueClient(URI endpointUri, Properties properties) {
        super(endpointUri, properties);
        backlog = new Backlog<>(this::storeValues);
    }

    protected Awaitable storeValues(List<KeyValuePair> keyValuePairs) {
        return send(new StoreValues(keyValuePairs));
    }

    @Override
    public Awaitable putValue(String key, Data<byte[]> value, Guarantee guarantee) {
        switch (guarantee) {
            case NONE:
                backlog.add(new KeyValuePair(key, value));
                return Awaitable.ready();
            case SENT:
                return backlog.add(new KeyValuePair(key, value));
            case STORED:
                sendRequestAndWait(new StoreValuesAndWait(singletonList(new KeyValuePair(key, value))));
                return Awaitable.ready();
            default:
                throw new UnsupportedOperationException("Unrecognized guarantee: " + guarantee);
        }
    }

    @Override
    public Data<byte[]> getValue(String key) {
        GetValueResult result = sendRequestAndWait(new GetValue(key));
        return result.getValue();
    }

    @Override
    public Awaitable deleteValue(String key) {
        return send(new DeleteValue(key));
    }
}
