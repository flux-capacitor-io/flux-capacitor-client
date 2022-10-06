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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.javaclient.MockException;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ThrowingErrorHandlerTest {

    private final ThrowingEventHandler eventHandler = new ThrowingEventHandler();
    private final TestFixture testFixture = TestFixture.createAsync(
            DefaultFluxCapacitor.builder().disableAutomaticAggregateCaching()
                    .configureDefaultConsumer(MessageType.EVENT, c -> c.toBuilder()
                    .errorHandler(new ThrowingErrorHandler()).build()), eventHandler);

    @Test
    void testTrackingStoppedDuringFirstEvent() {
        testFixture.whenEventsAreApplied("testAggregate", Object.class, "error")
                .expectThat(fc -> verify(fc.client().getTrackingClient(MessageType.EVENT), never())
                        .storePosition(any(), any(), anyLong()));
    }

    @Test
    void testTrackingStoppedDuringSecondEvent() {
        testFixture.whenEventsAreApplied("testAggregate", Object.class, 1, "error")
                .expectThat(fc -> {
                    TrackingClient trackingClient = fc.client().getTrackingClient(MessageType.EVENT);
                    verify(trackingClient).storePosition(any(), any(), eq((long) eventHandler.getFirstIndex()));
                    verify(trackingClient, never()).storePosition(any(), any(), eq((long) eventHandler.getSecondIndex()));
                });
    }

    @Getter
    private static class ThrowingEventHandler {
        private volatile Long firstIndex, secondIndex;

        @HandleEvent
        private void handle(Integer event, DeserializingMessage message) {
            firstIndex = message.getIndex();
        }

        @HandleEvent
        private void handle(String event, DeserializingMessage message) {
            secondIndex = message.getIndex();
            throw new MockException();
        }
    }

}