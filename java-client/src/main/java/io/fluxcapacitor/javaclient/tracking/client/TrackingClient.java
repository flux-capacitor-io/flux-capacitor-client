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
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.MessageBatch;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.SneakyThrows;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface TrackingClient extends AutoCloseable {

    @SneakyThrows
    default MessageBatch readAndWait(String consumer, String trackerId, Long lastIndex,
                                     ConsumerConfiguration configuration) {
        return read(consumer, trackerId, lastIndex, configuration).get();
    }

    CompletableFuture<MessageBatch> read(String consumer, String trackerId, Long lastIndex,
                                         ConsumerConfiguration trackingConfiguration);

    List<SerializedMessage> readFromIndex(long minIndex, int maxSize);

    CompletableFuture<ClaimSegmentResult> claimSegment(String consumer, String trackerId, Long lastIndex,
                                                       ConsumerConfiguration config);

    default CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex) {
        return storePosition(consumer, segment, lastIndex, Guarantee.STORED);
    }

    CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex, Guarantee guarantee);

    default CompletableFuture<Void> resetPosition(String consumer, long lastIndex) {
        return resetPosition(consumer, lastIndex, Guarantee.STORED);
    }

    CompletableFuture<Void> resetPosition(String consumer, long lastIndex, Guarantee guarantee);

    Position getPosition(String consumer);

    default CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch) {
        return disconnectTracker(consumer, trackerId, sendFinalEmptyBatch, Guarantee.SENT);
    }

    CompletableFuture<Void> disconnectTracker(String consumer, String trackerId, boolean sendFinalEmptyBatch, Guarantee guarantee);

    MessageType getMessageType();

    String getTopic();

    @Override
    void close();
}
