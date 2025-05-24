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

package io.fluxcapacitor.javaclient.tracking.client;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Metadata;
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
import io.fluxcapacitor.javaclient.configuration.client.WebSocketClient;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import jakarta.websocket.ClientEndpoint;
import lombok.Getter;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.MessageType.METRICS;

@ClientEndpoint
@Getter
public class WebsocketTrackingClient extends AbstractWebsocketClient implements TrackingClient {

    private final MessageType messageType;
    private final String topic;
    private final Metadata metricsMetadata;

    public WebsocketTrackingClient(String endPointUrl, WebSocketClient client, MessageType type, String topic) {
        this(URI.create(endPointUrl), client, type, topic, type != METRICS);
    }

    public WebsocketTrackingClient(URI endPointUri, WebSocketClient client, MessageType type, String topic, boolean sendMetrics) {
        super(endPointUri, client, sendMetrics, client.getClientConfig().getTrackingConfigs().get(type).getSessions());
        this.messageType = type;
        this.topic = topic;
        this.metricsMetadata = Metadata.of("messageType", type).with("topic", topic);
    }

    @Override
    public CompletableFuture<MessageBatch> read(String trackerId, Long lastIndex,
                                                ConsumerConfiguration configuration) {
        return this.<ReadResult>send(new Read(messageType,
                        configuration.getName(), trackerId, configuration.getMaxFetchSize(),
                        configuration.getMaxWaitDuration().toMillis(), configuration.getTypeFilter(),
                        configuration.filterMessageTarget(), configuration.ignoreSegment(),
                        configuration.singleTracker(), configuration.clientControlledIndex(), lastIndex,
                        Optional.ofNullable(configuration.getPurgeDelay()).map(Duration::toMillis).orElse(null)))
                .thenApply(ReadResult::getMessageBatch);
    }

    public CompletableFuture<ClaimSegmentResult> claimSegment(String trackerId, Long lastIndex,
                                                              ConsumerConfiguration config) {
        return send(new ClaimSegment(messageType,
                config.getName(), trackerId, config.getMaxWaitDuration().toMillis(), config.clientControlledIndex(),
                config.getTypeFilter(), config.filterMessageTarget(), lastIndex,
                Optional.ofNullable(config.getPurgeDelay()).map(Duration::toMillis).orElse(null)));
    }

    @Override
    public List<SerializedMessage> readFromIndex(long minIndex, int maxSize) {
        ReadFromIndexResult result = sendAndWait(new ReadFromIndex(minIndex, maxSize));
        return result.getMessages();
    }

    @Override
    public CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex, Guarantee guarantee) {
        return sendCommand(new StorePosition(messageType, consumer, segment, lastIndex, guarantee));
    }

    @Override
    public CompletableFuture<Void> resetPosition(String consumer, long lastIndex, Guarantee guarantee) {
        return sendCommand(new ResetPosition(messageType, consumer, lastIndex, guarantee));
    }

    @Override
    public Position getPosition(String consumer) {
        return this.<GetPositionResult>sendAndWait(new GetPosition(messageType, consumer)).getPosition();
    }

    @Override
    public CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch, Guarantee guarantee) {
        return sendCommand(new DisconnectTracker(messageType, consumer, trackerId, sendFinalEmptyBatch, guarantee));
    }

    @Override
    protected Metadata metricsMetadata() {
        return metricsMetadata;
    }

    @Override
    public void close() {
        close(true);
    }

    @Override
    public String toString() {
        return "%s-%s".formatted(super.toString(), messageType);
    }
}
