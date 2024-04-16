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

package io.fluxcapacitor.testserver.websocket;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.tracking.ClaimSegment;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.DisconnectTracker;
import io.fluxcapacitor.common.api.tracking.GetPosition;
import io.fluxcapacitor.common.api.tracking.GetPositionResult;
import io.fluxcapacitor.common.api.tracking.Position;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadFromIndex;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexResult;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.ResetPosition;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.common.tracking.DefaultTrackingStrategy;
import io.fluxcapacitor.common.tracking.InMemoryPositionStore;
import io.fluxcapacitor.common.tracking.MessageStore;
import io.fluxcapacitor.common.tracking.PositionStore;
import io.fluxcapacitor.common.tracking.TrackingStrategy;
import io.fluxcapacitor.common.tracking.WebSocketTracker;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@AllArgsConstructor
public class ConsumerEndpoint extends WebsocketEndpoint {

    private final TrackingStrategy trackingStrategy;
    private final MessageStore messageStore;
    private final PositionStore positionStore;
    private final MessageType messageType;

    public ConsumerEndpoint(MessageStore messageStore, MessageType messageType) {
        this(new DefaultTrackingStrategy(messageStore), messageStore, new InMemoryPositionStore(), messageType);
    }

    @Handle
    void handle(Read read, Session session) {
        trackingStrategy.getBatch(
                new WebSocketTracker(read, messageType, getClientId(session), session.getId(), (batch, p)
                        -> doSendResult(session, new ReadResult(read.getRequestId(), batch))), positionStore);
    }

    @Handle
    void handle(ClaimSegment read, Session session) {
        trackingStrategy.claimSegment(
                new WebSocketTracker(read, messageType, getClientId(session), session.getId(), (batch, p) ->
                        doSendResult(session, new ClaimSegmentResult(read.getRequestId(), Optional.ofNullable(p)
                                .orElseGet(Position::newPosition), batch.getSegment()))), positionStore);
    }

    @Handle
    CompletableFuture<Void> handle(StorePosition storePosition) {
        return positionStore.storePosition(storePosition.getConsumer(), storePosition.getSegment(),
                                    storePosition.getLastIndex());
    }

    @Handle
    CompletableFuture<Void> handle(ResetPosition resetPosition) {
        return positionStore.resetPosition(resetPosition.getConsumer(), resetPosition.getLastIndex());
    }

    @Handle
    void handle(DisconnectTracker disconnectTracker) {
        trackingStrategy.disconnectTrackers(
                t -> Objects.equals(t.getConsumerName(), disconnectTracker.getConsumer())
                     && Objects.equals(t.getTrackerId(), disconnectTracker.getTrackerId()),
                disconnectTracker.isSendFinalEmptyBatch());
    }

    @Handle
    ReadFromIndexResult handle(ReadFromIndex read) {
        return new ReadFromIndexResult(read.getRequestId(),
                                       messageStore.readFromIndex(read.getMinIndex(), read.getMaxSize()));
    }

    @Handle
    GetPositionResult handle(GetPosition read) {
        return new GetPositionResult(read.getRequestId(), positionStore.position(read.getConsumer()));
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        trackingStrategy.disconnectTrackers(t -> t instanceof WebSocketTracker
                                                 && ((WebSocketTracker) t).getSessionId().equals(session.getId()),
                                            false);
        super.onClose(session, closeReason);
    }

    @Override
    protected void shutDown() {
        trackingStrategy.disconnectTrackers(t -> true, false);
        super.shutDown();
    }

    @Override
    public String toString() {
        return "ConsumerEndpoint{logType='" + messageType + "'}";
    }
}
