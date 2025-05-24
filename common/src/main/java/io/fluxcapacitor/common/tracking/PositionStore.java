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

package io.fluxcapacitor.common.tracking;

import io.fluxcapacitor.common.api.tracking.Position;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for storing and retrieving {@link Position} objects representing the last processed message indexes
 * per segment for a given consumer.
 * <p>
 * A {@link Position} tracks consumption progress on a per-segment basis. This enables distributed, parallel
 * consumption and accurate replay/resume behavior in Flux Capacitor.
 */
public interface PositionStore {

    /**
     * Stores the latest processed index for a given segment range and consumer.
     *
     * @param consumer  the consumer name (e.g., the tracking processor)
     * @param segment   the segment range for which this index applies
     * @param lastIndex the last index successfully handled
     * @return a future that completes when the position is stored
     */
    CompletableFuture<Void> storePosition(String consumer, int[] segment, long lastIndex);

    /**
     * Resets the position of the consumer for all segments to the given index.
     *
     * @param consumer  the consumer name
     * @param lastIndex the new index to assign for all segments
     * @return a future that completes when the reset is applied
     */
    CompletableFuture<Void> resetPosition(String consumer, long lastIndex);

    /**
     * Retrieves the full multi-segment position for the given consumer.
     *
     * @param consumer the consumer name
     * @return the current {@link Position} of that consumer
     */
    Position position(String consumer);

    /**
     * Closes any underlying resources held by the store.
     */
    void close();
}
