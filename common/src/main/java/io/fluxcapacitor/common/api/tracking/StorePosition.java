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

package io.fluxcapacitor.common.api.tracking;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Command;
import lombok.Value;

/**
 * Command sent to the Flux platform to update the tracked position for a specific consumer and segment range.
 * <p>
 * This is typically used after a {@code Tracker} has successfully processed a
 * {@link io.fluxcapacitor.common.api.tracking.MessageBatch}, allowing the system to record the latest confirmed index.
 * <p>
 * <strong>Important:</strong> The {@code lastIndex} can only move forward. Attempts to store a lower index than
 * currently registered will be ignored. This ensures resilience against out-of-order or stale tracker activity (e.g.
 * due to temporary disconnects), and helps prevent split-brain scenarios.
 * <p>
 * If you need to explicitly lower a segment’s index (e.g. for replay or recovery purposes), use the
 * {@link io.fluxcapacitor.common.api.tracking.ResetPosition} command instead.
 */
@Value
public class StorePosition extends Command {

    /**
     * The type of message being tracked (e.g. {@code EVENT}, {@code COMMAND}, etc.).
     */
    MessageType messageType;

    /**
     * The name of the consumer group for which the position is being stored.
     */
    String consumer;

    /**
     * The segment range [start, end) that the tracker is responsible for.
     */
    int[] segment;

    /**
     * The highest index that has been successfully processed by the tracker. Must be greater than or equal to the
     * previously stored index for this segment range.
     */
    long lastIndex;

    /**
     * The delivery guarantee level to apply when storing the position. See {@link io.fluxcapacitor.common.Guarantee}.
     */
    Guarantee guarantee;

    /**
     * Returns a routing key based on the message type and consumer name. This key is used for load balancing and
     * message partitioning in the Flux platform.
     */
    @Override
    public String routingKey() {
        return "%s_%s".formatted(messageType, consumer);
    }
}
