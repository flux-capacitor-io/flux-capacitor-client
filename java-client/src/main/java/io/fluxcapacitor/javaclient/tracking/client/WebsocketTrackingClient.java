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

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.DisconnectTracker;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadFromIndex;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexResult;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.ResetPosition;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.common.api.tracking.TrackingStrategy;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.common.websocket.JsonDecoder;
import io.fluxcapacitor.javaclient.common.websocket.JsonEncoder;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WebsocketTrackingClient extends AbstractWebsocketClient implements TrackingClient {

    public WebsocketTrackingClient(String endPointUrl) {
        this(URI.create(endPointUrl));
    }

    public WebsocketTrackingClient(URI endPointUri) {
        super(endPointUri);
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId, int maxSize, Duration maxTimeout,
                                                String typeFilter, boolean ignoreMessageTarget,
                                                TrackingStrategy strategy) {
        CompletableFuture<ReadResult> readResult = sendRequest(new Read(
                consumer, trackerId, maxSize, maxTimeout.toMillis(), typeFilter, 
                ignoreMessageTarget, strategy));
        return readResult.thenApply(ReadResult::getMessageBatch);
    }

    @Override
    public List<SerializedMessage> readFromIndex(long minIndex, int maxSize) {
        ReadFromIndexResult result = sendRequestAndWait(new ReadFromIndex(minIndex, maxSize));
        return result.getMessages();
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        return send(new StorePosition(consumer, segment, lastIndex));
    }

    @Override
    public Awaitable resetPosition(String consumer, long lastIndex) {
        return send(new ResetPosition(consumer, lastIndex));
    }

    @Override
    public Awaitable disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch) {
        return send(new DisconnectTracker(consumer, trackerId, sendFinalEmptyBatch));
    }

    @Override
    public void close() {
        close(true);
    }
}
