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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.ClaimSegment;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.DisconnectTracker;
import io.fluxcapacitor.common.api.tracking.GetPosition;
import io.fluxcapacitor.common.api.tracking.GetPositionResult;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadFromIndex;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexResult;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.ResetPosition;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.javaclient.common.websocket.AbstractWebsocketClient;
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient.ClientConfig;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.Awaitable.fromFuture;
import static io.fluxcapacitor.common.MessageType.METRICS;

public class WebsocketTrackingClient extends AbstractWebsocketClient implements TrackingClient {

    public WebsocketTrackingClient(String endPointUrl, ClientConfig clientConfig, MessageType type) {
        this(URI.create(endPointUrl), clientConfig, type);
    }

    public WebsocketTrackingClient(URI endPointUri, ClientConfig clientConfig, MessageType type) {
        super(endPointUri, clientConfig, type != METRICS, clientConfig.getTrackingConfigs().get(type).getSessions());
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId, Long lastIndex,
                                                ConsumerConfiguration configuration) {
        return this.<ReadResult>send(new Read(
                        consumer, trackerId, configuration.getMaxFetchBatchSize(),
                        configuration.getMaxWaitDuration().toMillis(), configuration.getTypeFilter(),
                        configuration.ignoreMessageTarget(), configuration.ignoreSegment(),
                        configuration.getReadStrategy(), lastIndex,
                        Optional.ofNullable(configuration.getPurgeDelay()).map(Duration::toMillis).orElse(null)))
                .thenApply(ReadResult::getMessageBatch);
    }

    public CompletableFuture<ClaimSegmentResult> claimSegment(String consumer, String trackerId, Long lastIndex,
                                           ConsumerConfiguration config) {
        return send(new ClaimSegment(
                consumer, trackerId, config.getMaxWaitDuration().toMillis(), config.getTypeFilter(),
                config.ignoreMessageTarget(), config.getReadStrategy(), lastIndex,
                Optional.ofNullable(config.getPurgeDelay()).map(Duration::toMillis).orElse(null)));
    }

    @Override
    public List<SerializedMessage> readFromIndex(long minIndex, int maxSize) {
        ReadFromIndexResult result = sendAndWait(new ReadFromIndex(minIndex, maxSize));
        return result.getMessages();
    }

    @Override
    public Awaitable storePosition(String consumer, int[] segment, long lastIndex) {
        return fromFuture(send(new StorePosition(consumer, segment, lastIndex)), Duration.ofSeconds(60));
    }

    @Override
    public Awaitable resetPosition(String consumer, long lastIndex) {
        return fromFuture(send(new ResetPosition(consumer, lastIndex)), Duration.ofSeconds(60));
    }

    @Override
    public Position getPosition(String consumer) {
        return this.<GetPositionResult>sendAndWait(new GetPosition(consumer)).getPosition();
    }

    @Override
    public Awaitable disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch) {
        return sendAndForget(new DisconnectTracker(consumer, trackerId, sendFinalEmptyBatch));
    }

    @Override
    public void close() {
        close(true);
    }
}
