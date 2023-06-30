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

package io.fluxcapacitor.testserver.endpoints;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.VoidResult;
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
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
@AllArgsConstructor
public class ConsumerEndpoint extends WebsocketEndpoint {

    private final InMemoryMessageStore store;
    private final MessageType messageType;

    @Handle
    public void handle(Read read, Session session) {
        store.read(new WebSocketTrackerRead(read, getClientId(session), session.getId(), messageType)).whenComplete(
                (b, e) -> {
                    if (e != null) {
                        log.error("Failed to complete read", e);
                    } else {
                        sendResult(session, new ReadResult(read.getRequestId(), b));
                    }
                });
    }

    @Handle
    public void handle(ClaimSegment read, Session session) {
        store.claimSegment(new WebSocketTrackerRead(read, getClientId(session), session.getId(), messageType))
                .whenComplete((b, e) -> {
                    if (e != null) {
                        log.error("Failed to complete claim segment", e);
                    } else {
                        sendResult(session, new ClaimSegmentResult(read.getRequestId(),
                                                                   store.getPosition(read.getConsumer()), b));
                    }
                });
    }

    @Handle
    public VoidResult handle(StorePosition storePosition) {
        store.storePosition(storePosition.getConsumer(), storePosition.getSegment(), storePosition.getLastIndex());
        return new VoidResult(storePosition.getRequestId());
    }

    @Handle
    public VoidResult handle(ResetPosition resetPosition) {
        store.resetPosition(resetPosition.getConsumer(), resetPosition.getLastIndex());
        return new VoidResult(resetPosition.getRequestId());
    }

    @Handle
    public void handle(DisconnectTracker disconnectTracker) {
        store.disconnectTracker(disconnectTracker.getConsumer(), disconnectTracker.getTrackerId(),
                                disconnectTracker.isSendFinalEmptyBatch());
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
