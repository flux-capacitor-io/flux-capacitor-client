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

import io.fluxcapacitor.javaclient.configuration.DefaultFluxCapacitor;
import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StallingBatchInterceptorTest {

    private static final Duration stallingDuration = Duration.ofSeconds(10);
    private final MockEventHandler handler = spy(new MockEventHandler());

    private final TestFixture testFixture =
            TestFixture.createAsync(DefaultFluxCapacitor.builder().configureDefaultConsumer(
                    EVENT,
                    c -> c.toBuilder().batchInterceptor(
                            StallingBatchInterceptor.builder()
                                    .desiredBatchSize(2).maximumStallingDuration(stallingDuration)
                                    .retryFrequency(Duration.ofMillis(10)).build())
                            .build()), handler).consumerTimeout(Duration.ofMillis(100));

    @Test
    void testLargeBatchPassed() {
        testFixture.whenDomainEvents("test", 0, 1)
                .expectThat(fc -> verify(handler, times(2)).handle(any()))
                .expectThat(fc -> verify(fc.client().getTrackingClient(EVENT)).storePosition(any(), any(), anyLong()));
    }

    @Test
    void testSmallBatchStalled() {
        testFixture.whenDomainEvents("test", 0)
                .expectThat(fc -> verify(handler, never()).handle(any()))
                .expectThat(fc -> verify(fc.client().getTrackingClient(EVENT), never())
                        .storePosition(any(), any(), anyLong()));
    }

    @Test
    void testSmallBatchPassedAfterTimeout() {
        testFixture.givenDomainEvents("test", 0)
                .whenTimeElapses(stallingDuration)
                .expectThat(fc -> verify(handler, times(1)).handle(any()))
                .expectThat(fc -> verify(fc.client().getTrackingClient(EVENT)).storePosition(any(), any(), anyLong()));
    }

    private static class MockEventHandler {
        @HandleEvent
        void handle(Integer event) {
        }
    }
}