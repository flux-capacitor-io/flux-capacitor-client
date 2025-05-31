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

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * A no-op implementation of the {@link FlowRegulator} interface that never requests a pause.
 * <p>
 * This regulator allows the consumer to fetch and consume messages without any delay or throttling. It is the default
 * regulator used in most cases, where message flow is unbounded or externally managed (e.g. through batch sizes, thread
 * limits, or backpressure at other levels).
 *
 * <p><strong>Behavior:</strong>
 * <ul>
 *   <li>{@link #pauseDuration()} always returns {@link java.util.Optional#empty()}, indicating the consumer should never pause.</li>
 *   <li>Ideal for high-throughput scenarios where flow regulation is unnecessary or handled outside the Flux Capacitor runtime.</li>
 *   <li>Thread-safe and stateless — a single shared instance is used throughout the application.</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * This is the default unless overridden in {@link io.fluxcapacitor.javaclient.tracking.Consumer} or
 * {@link io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration}.
 *
 * @see FlowRegulator
 */
@EqualsAndHashCode
public class NoOpFlowRegulator implements FlowRegulator {

    /**
     * Shared singleton instance of the no-op regulator.
     */
    @Getter
    private static final NoOpFlowRegulator instance = new NoOpFlowRegulator();
}
