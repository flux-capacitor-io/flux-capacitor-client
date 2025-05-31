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
 * Command used to explicitly disconnect a tracker from the Flux platform and release any claimed message segments.
 * <p>
 * This is typically only used in advanced scenarios, such as when working with external trackers that integrate with
 * Flux via long-polling or custom message ingestion mechanisms.
 *
 * <h2>Use Cases</h2>
 * <ul>
 *     <li>Releasing a segment claim manually, especially when the tracker may not shut down gracefully</li>
 *     <li>Forcing rebalancing of segment claims across multiple tracker instances</li>
 *     <li>Implementing robust external consumer management (e.g. using webhooks or polling APIs)</li>
 * </ul>
 *
 * <h3>Segment Reassignment</h3>
 * When a tracker is disconnected, any claimed segments are released and may be reassigned to other trackers.
 *
 * <h3>Optional Final Notification</h3>
 * If {@link #sendFinalEmptyBatch} is {@code true}, a final empty {@link io.fluxcapacitor.common.api.tracking.MessageBatch}
 * is sent to the tracker prior to disconnection. This allows graceful shutdown or checkpointing.
 *
 * @see ClaimSegment
 * @see DisconnectTracker
 */
@Value
public class DisconnectTracker extends Command {

    /**
     * The type of message stream the tracker is consuming from.
     */
    MessageType messageType;

    /**
     * The name of the consumer to which the tracker belongs.
     */
    String consumer;

    /**
     * The unique identifier of the tracker to disconnect.
     */
    String trackerId;

    /**
     * If true, an empty message batch will be sent to the tracker before disconnection to allow graceful cleanup.
     */
    boolean sendFinalEmptyBatch;

    /**
     * The delivery guarantee for the disconnect command (e.g., ensure it's stored or just sent).
     */
    Guarantee guarantee;

    @Override
    public String routingKey() {
        return "%s_%s".formatted(messageType, consumer);
    }
}
