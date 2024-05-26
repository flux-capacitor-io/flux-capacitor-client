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
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.tracking.DefaultTrackingStrategy;
import io.fluxcapacitor.common.tracking.HasMessageStore;
import io.fluxcapacitor.common.tracking.InMemoryPositionStore;
import io.fluxcapacitor.common.tracking.MessageStore;
import io.fluxcapacitor.common.tracking.PositionStore;
import io.fluxcapacitor.common.tracking.TrackingStrategy;
import io.fluxcapacitor.common.tracking.WebSocketTracker;
import io.fluxcapacitor.javaclient.publishing.client.GatewayClient;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@AllArgsConstructor
public class LocalTrackingClient implements TrackingClient, GatewayClient, HasMessageStore {
    private final TrackingStrategy trackingStrategy;
    @Getter
    private final MessageStore messageStore;
    private final PositionStore positionStore;

    @Getter
    private final MessageType messageType;

    public LocalTrackingClient(MessageType messageType, Duration messageExpiration) {
        this.messageStore = new InMemoryMessageStore(messageType, messageExpiration);
        this.trackingStrategy = new DefaultTrackingStrategy(messageStore);
        this.positionStore = new InMemoryPositionStore();
        this.messageType = messageType;
    }

    public LocalTrackingClient(MessageStore messageStore, MessageType messageType) {
        this.messageStore = messageStore;
        this.messageType = messageType;
        this.trackingStrategy = new DefaultTrackingStrategy(messageStore);
        this.positionStore = new InMemoryPositionStore();
    }

    @Override
    public Registration registerMonitor(Consumer<List<SerializedMessage>> monitor) {
        return messageStore.registerMonitor(monitor);
    }

    @Override
    public CompletableFuture<Void> append(Guarantee guarantee, SerializedMessage... messages) {
        return messageStore.append(messages);
    }

    @Override
    public CompletableFuture<MessageBatch> read(String consumer, String trackerId, Long lastIndex,
                                                ConsumerConfiguration config) {
        CompletableFuture<MessageBatch> result = new CompletableFuture<>();
        trackingStrategy.getBatch(
                new WebSocketTracker(new Read(messageType, consumer, trackerId, config.getMaxFetchSize(),
                                              config.getMaxWaitDuration().toMillis(), config.getTypeFilter(),
                                              config.filterMessageTarget(), config.ignoreSegment(),
                                              config.singleTracker(), config.clientControlledIndex(), lastIndex,
                                              Optional.ofNullable(config.getPurgeDelay()).map(Duration::toMillis)
                                                      .orElse(null)),
                                     messageType, ManagementFactory.getRuntimeMXBean().getName(),
                                     null, result::complete), positionStore);
        return result;
    }

    @Override
    public List<SerializedMessage> readFromIndex(long minIndex, int maxSize) {
        return messageStore.getBatch(minIndex, maxSize, true);
    }

    @Override
    public CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex, Guarantee guarantee) {
        return positionStore.storePosition(consumer, segment, lastIndex);
    }

    @Override
    public CompletableFuture<Void> resetPosition(String consumer, long lastIndex, Guarantee guarantee) {
        return positionStore.resetPosition(consumer, lastIndex);
    }

    @Override
    public Position getPosition(String consumer) {
        return positionStore.position(consumer);
    }

    @Override
    public CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch,
                                                     Guarantee guarantee) {
        trackingStrategy.disconnectTrackers(t -> t.getTrackerId().equalsIgnoreCase(trackerId), sendFinalEmptyBatch);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void close() {
        messageStore.close();
        trackingStrategy.close();
    }
}
