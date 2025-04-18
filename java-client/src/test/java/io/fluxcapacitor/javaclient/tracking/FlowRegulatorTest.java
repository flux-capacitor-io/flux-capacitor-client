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

package io.fluxcapacitor.javaclient.tracking;

import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import io.fluxcapacitor.javaclient.tracking.metrics.HandleMessageEvent;
import io.fluxcapacitor.javaclient.tracking.metrics.PauseTrackerEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

class FlowRegulatorTest {

    @Nested
    class Pausing {
        final TestFixture testFixture = TestFixture.createAsync();

        @Test
        void consumerIsPaused() {
            testFixture
                    .consumerTimeout(Duration.ofMillis(800))
                    .registerHandlers(PauseOnceHandler.class)
                    .whenEvent("123")
                    .expectMetrics(PauseTrackerEvent.class)
                    .expectNoMetricsLike(HandleMessageEvent.class);
        }

        @Test
        void consumerIsContinuedAfterDelay() {
            testFixture
                    .registerHandlers(PauseOnceHandler.class)
                    .whenEvent("123")
                    .expectMetrics(HandleMessageEvent.class);
        }

        @Consumer(name = "MyHandler", flowRegulator = PauseOnce.class)
        static class PauseOnceHandler {
            @HandleEvent
            void handle(String ignored) {
            }
        }

        static class PauseOnce implements FlowRegulator {
            final AtomicBoolean pausedOnce = new AtomicBoolean();
            @Override
            public Optional<Duration> pauseDuration() {
                if (pausedOnce.compareAndSet(false, true)) {
                    return Optional.of(Duration.ofMillis(800));
                }
                return Optional.empty();
            }
        }
    }



}