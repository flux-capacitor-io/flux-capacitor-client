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

package io.fluxcapacitor.javaclient.keyvalue.websocket;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.Backlog;
import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.keyvalue.*;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketService;
import io.fluxcapacitor.javaclient.keyvalue.KeyValueClient;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.List;

@Slf4j
@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebsocketKeyValueClient extends AbstractWebsocketService implements KeyValueClient {

    private final Backlog<KeyValuePair> backlog;

    public WebsocketKeyValueClient(String endPointUrl) {
        this(URI.create(endPointUrl));
    }

    public WebsocketKeyValueClient(URI endpointUri) {
        super(endpointUri);
        backlog = new Backlog<>(this::storeValues);
    }

    protected Awaitable storeValues(List<KeyValuePair> keyValuePairs) throws Exception {
        getSession().getBasicRemote().sendObject(new StoreValues(keyValuePairs));
        return Awaitable.ready();
    }

    @Override
    public Awaitable putValue(String key, Data<byte[]> value) {
        return backlog.add(new KeyValuePair(key, value));
    }

    @Override
    public Data<byte[]> getValue(String key) {
        GetValueResult result = sendRequest(new GetValue(key));
        return result.getValue();
    }

    @Override
    public Awaitable deleteValue(String key) {
        try {
            getSession().getBasicRemote().sendObject(new DeleteValue(key));
        } catch (Exception e) {
            log.warn("Could not delete value {}", key, e);
            return () -> {
                throw e;
            };
        }
        return Awaitable.ready();
    }
}
