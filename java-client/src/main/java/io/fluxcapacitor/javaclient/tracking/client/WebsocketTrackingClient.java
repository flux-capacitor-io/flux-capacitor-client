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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketService;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.time.Duration;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebsocketTrackingClient extends AbstractWebsocketService implements TrackingClient {

    public WebsocketTrackingClient(String endPointUrl) {
        this(URI.create(endPointUrl));
    }

    public WebsocketTrackingClient(URI endPointUri) {
        super(endPointUri);
    }

    @Override
    public MessageBatch read(String consumer, int channel, int maxSize, Duration maxTimeout) {
        ReadResult readResult = sendRequest(new Read(consumer, channel, maxSize, maxTimeout.toMillis()));
        return readResult.getMessageBatch();
    }

    @Override
    public void storePosition(String consumer, int[] segment, long lastIndex) {
        try {
            getSession().getBasicRemote().sendObject(new StorePosition(consumer, segment, lastIndex));
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Failed to store position of processor %s", consumer), e);
        }
    }
}
