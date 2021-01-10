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
import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ThrowingErrorHandlerTest {

    private final TestFixture testFixture = TestFixture.createAsync(
            DefaultFluxCapacitor.builder().configureDefaultConsumer(MessageType.EVENT, c -> c.toBuilder()
                    .errorHandler(new ThrowingErrorHandler()).build()),
            new ThrowingEventHandler());

    @Test
    void testTrackingStoppedDuringFirstEvent() {
        testFixture.whenDomainEvents("testAggregate", "error")
                .expectThat(fc -> verify(fc.client().getTrackingClient(MessageType.EVENT), never())
                        .storePosition(any(), any(), anyLong()));
    }

    @Test
    void testTrackingStoppedDuringSecondEvent() {
        testFixture.whenDomainEvents("testAggregate", 1, "error")
                .expectThat(fc -> {
                    TrackingClient trackingClient = fc.client().getTrackingClient(MessageType.EVENT);
                    verify(trackingClient).storePosition(any(), any(), eq(0L));
                    verify(trackingClient, never()).storePosition(any(), any(), eq(1L));
                });
    }

    private static class ThrowingEventHandler {
        @HandleEvent
        private void handle(Integer event) {
        }

        @HandleEvent
        private void handle(String event) {
            throw new IllegalArgumentException();
        }
    }

}