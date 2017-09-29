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

package io.fluxcapacitor.javaclient.tracking.websocket;

import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.common.serialization.websocket.JsonDecoder;
import io.fluxcapacitor.common.serialization.websocket.JsonEncoder;
import io.fluxcapacitor.javaclient.common.connection.AbstractWebsocketService;
import io.fluxcapacitor.javaclient.tracking.ConsumerService;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebsocketConsumerService extends AbstractWebsocketService implements ConsumerService {

    public WebsocketConsumerService(String endPointUrl) {
        this(URI.create(endPointUrl));
    }

    public WebsocketConsumerService(URI endPointUri) {
        super(endPointUri);
    }

    @Override
    public MessageBatch read(String processor, int channel, int maxSize, int maxTimeout, TimeUnit timeUnit) {
        ReadResult readResult = sendRequest(
                new Read(processor, channel, maxSize, (int) TimeUnit.MILLISECONDS.convert(maxTimeout, timeUnit)));
        return readResult.getMessageBatch();
    }

    @Override
    public void storePosition(String processor, int[] segment, long lastIndex) {
        try {
            getSession().getBasicRemote().sendObject(new StorePosition(processor, segment, lastIndex));
        } catch (Exception e) {
            throw new IllegalStateException(String.format("Failed to store position of processor %s", processor), e);
        }
    }
}
