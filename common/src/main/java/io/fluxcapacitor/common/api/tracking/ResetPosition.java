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
 * Command to forcibly reset the tracked position for a given consumer and message type.
 * <p>
 * Unlike {@link StorePosition}, which only allows the tracked index to move forward, this command allows setting a
 * lower {@code lastIndex}, effectively rewinding the consumer’s position.
 * <p>
 * This is a powerful operation primarily intended for administrative or recovery scenarios. Common use cases include:
 * <ul>
 *   <li>Reprocessing past messages after fixing a bug or applying a new projection</li>
 *   <li>Resetting a consumer to rehydrate state (e.g. reloading an in-memory cache)</li>
 *   <li>Clearing segment state after corruption or unexpected termination</li>
 * </ul>
 *
 * <h2>Preferred Alternatives</h2>
 * For new consumers or reconfigured tracker instances, it is typically preferred to set the initial tracking index
 * via {@code ConsumerConfiguration} or the {@code @Consumer} annotation.
 * These approaches are safer and declarative, and avoid the potential side effects of resetting an active consumer.
 * <p>
 * <strong>Warning:</strong> Improper use may lead to duplicate processing. Ensure that consumers are idempotent or
 * properly deduplicated before issuing a reset.
 */
@Value
public class ResetPosition extends Command {

    /**
     * The type of messages being consumed (e.g. {@code EVENT}, {@code COMMAND}, etc.).
     */
    MessageType messageType;

    /**
     * The name of the consumer whose position should be reset.
     */
    String consumer;

    /**
     * The new index to reset to. Messages with an index greater than this value will be processed again.
     */
    long lastIndex;

    /**
     * The guarantee level to use when issuing this reset command. See {@link io.fluxcapacitor.common.Guarantee}.
     */
    Guarantee guarantee;

    /**
     * Returns a routing key used for partitioning the reset command by consumer and message type.
     */
    @Override
    public String routingKey() {
        return "%s_%s".formatted(messageType, consumer);
    }
}
