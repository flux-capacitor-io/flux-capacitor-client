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

package io.fluxcapacitor.javaclient.tracking.metrics;

import io.fluxcapacitor.javaclient.test.TestFixture;
import io.fluxcapacitor.javaclient.tracking.Consumer;
import io.fluxcapacitor.javaclient.tracking.handling.HandleEvent;
import org.junit.jupiter.api.Test;

class TrackerMonitorTest {
    @Test
    void trackerMetricsPublished() {
        @Consumer(name = "custom-consumer")
        class Handler {
            @HandleEvent
            void handle(String ignored) {
            }
        }
        TestFixture.createAsync(new Handler()).whenEvent("test")
                .<ProcessBatchEvent>expectMetric(
                        e -> e.getConsumer().equals("custom-consumer") && e.getBatchSize() == 1)
                .expectMetrics(HandleMessageEvent.class);
    }

    @Test
    void blockHandlerMetrics() {
        @Consumer(name = "MetricsBlocked-consumer", handlerInterceptors = DisableMetrics.class)
        class Handler {
            @HandleEvent
            void handle(String ignored) {
            }
        }
        TestFixture.createAsync(new Handler()).whenEvent("test").expectNoMetricsLike(HandleMessageEvent.class);
    }

    @Test
    void blockBatchMetrics() {
        @Consumer(name = "MetricsBlocked-consumer", batchInterceptors = DisableMetrics.class)
        class Handler {
            @HandleEvent
            void handle(String ignored) {
            }
        }
        TestFixture.createAsync(new Handler()).whenEvent("test")
                .expectNoMetricsLike(HandleMessageEvent.class)
                .expectNoMetricsLike(ProcessBatchEvent.class);
    }

}