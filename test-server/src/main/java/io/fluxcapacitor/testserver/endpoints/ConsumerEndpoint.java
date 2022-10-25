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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.tracking.ClaimSegment;
import io.fluxcapacitor.common.api.tracking.ClaimSegmentResult;
import io.fluxcapacitor.common.api.tracking.DisconnectTracker;
import io.fluxcapacitor.common.api.tracking.GetPosition;
import io.fluxcapacitor.common.api.tracking.GetPositionResult;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadFromIndex;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexResult;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.ResetPosition;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;
import io.fluxcapacitor.testserver.Handle;
import io.fluxcapacitor.testserver.WebsocketEndpoint;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.websocket.CloseReason;
import javax.websocket.Session;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
@AllArgsConstructor
public class ConsumerEndpoint extends WebsocketEndpoint {

    private final InMemoryMessageStore store;
    private final MessageType messageType;

    @Handle
    public CompletableFuture<?> handle(Read read, Session session) {
        return store.read(new WebSocketTrackerRead(read, getClientId(session), session.getId(), messageType))
                .thenApply(b -> new ReadResult(read.getRequestId(), b));
    }

    @Handle
    public CompletableFuture<?> handle(ClaimSegment read, Session session) {
        return store.claimSegment(new WebSocketTrackerRead(read, getClientId(session), session.getId(), messageType))
                .thenApply(b -> new ClaimSegmentResult(read.getRequestId(),
                                                                   store.getPosition(read.getConsumer()), b));
    }

    @Handle
    public void handle(StorePosition storePosition) {
        store.storePosition(storePosition.getConsumer(), storePosition.getSegment(), storePosition.getLastIndex());
    }

    @Handle
    public void handle(ResetPosition resetPosition) {
        store.resetPosition(resetPosition.getConsumer(), resetPosition.getLastIndex());
    }

    @Handle
    public void handle(DisconnectTracker disconnectTracker) {
        store.disconnectTracker(disconnectTracker.getConsumer(), disconnectTracker.getTrackerId(),
                                disconnectTracker.isSendFinalEmptyBatch(), disconnectTracker.getGuarantee());
    }

    @Handle
    public ReadFromIndexResult handle(ReadFromIndex read) {
        List<SerializedMessage> batch = store.readFromIndex(read.getMinIndex() - 1L, read.getMaxSize());
        return new ReadFromIndexResult(read.getRequestId(), batch);
    }

    @Handle
    public GetPositionResult handle(GetPosition getPosition) {
        return new GetPositionResult(getPosition.getRequestId(), store.getPosition(getPosition.getConsumer()));
    }

    @Override
    public void onClose(Session session, CloseReason closeReason) {
        super.onClose(session, closeReason);
        store.<WebSocketTrackerRead>disconnectTrackersMatching(t -> Objects.equals(t.getSessionId(), session.getId()));
    }
}
