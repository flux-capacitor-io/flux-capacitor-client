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
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.Properties;
import io.fluxcapacitor.javaclient.tracking.TrackingConfiguration;

import javax.websocket.ClientEndpoint;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@ClientEndpoint
public class WebsocketTrackingClient extends AbstractWebsocketClient implements TrackingClient {

    public WebsocketTrackingClient(String endPointUrl, Properties properties) {
        this(URI.create(endPointUrl), properties);
    }

    public WebsocketTrackingClient(URI endPointUri, Properties properties) {
        super(endPointUri, properties);
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId, Long lastIndex,
                                                TrackingConfiguration configuration) {
        CompletableFuture<ReadResult> readResult = sendRequest(new Read(
                consumer, trackerId, configuration.getMaxFetchBatchSize(), configuration.getMaxWaitDuration().toMillis(), configuration.getTypeFilter(),
                configuration.ignoreMessageTarget(), configuration.getReadStrategy(), lastIndex,
                Optional.ofNullable(configuration.getPurgeDelay()).map(Duration::toMillis).orElse(null)));
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
