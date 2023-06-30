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

import java.util.function.Predicate;

class TrackerMonitorTest {
    final TestFixture testFixture = TestFixture.createAsync(new Handler());

    @Test
    void processBatchMetricPublished() {
        testFixture.whenEvent("test").expectMetrics((Predicate<ProcessBatchEvent>) e ->
                e.getConsumer().equals("custom-consumer") && e.getBatchSize() == 1);
    }

    @Consumer(name = "custom-consumer")
    static class Handler {
        @HandleEvent
        void handle(String ignored) {
        }
    }
}