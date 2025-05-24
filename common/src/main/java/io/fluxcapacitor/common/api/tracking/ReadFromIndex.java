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

import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Request to fetch a batch of messages from a given log starting from a given index.
 * <p>
 * This low-level API is typically used by administrators or tools that want to directly inspect messages in a
 * message log without relying on a registered consumer or tracker.
 * <p>
 * This request bypasses the segment claiming and tracker coordination logic normally used in streaming consumption.
 * It is useful for debugging, audit trails, or manual inspection of persisted messages.
 */
@Value
@EqualsAndHashCode(callSuper = true)
public class ReadFromIndex extends Request {
    /**
     * The minimum index (inclusive) to start reading messages from.
     */
    long minIndex;

    /**
     * The maximum number of messages to return in a single batch.
     * <p>
     * Note that the actual number of returned messages may be smaller if there are fewer available at or above
     * the specified index.
     */
    int maxSize;
}
