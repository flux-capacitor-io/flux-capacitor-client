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

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.NonFinal;

/**
 * Command to read a batch of messages from the Flux platform for a given consumer and tracker.
 * <p>
 * This is a low-level API, typically only used internally in Flux by client-side tracking mechanisms or in advanced
 * Flux projects to support external consumers.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@NonFinal
public class Read extends Request {

    /**
     * The type of messages to read (e.g. EVENT, COMMAND, QUERY, etc.).
     */
    MessageType messageType;

    /**
     * The logical name of the consumer performing the read.
     */
    String consumer;

    /**
     * Unique ID for the specific tracker instance.
     */
    String trackerId;

    /**
     * Maximum number of messages to return in a single batch.
     */
    int maxSize;

    /**
     * Maximum time to wait for new messages, in milliseconds.
     */
    long maxTimeout;

    /**
     * Optional filter that limits messages by payload type (matches class name prefix).
     */
    String typeFilter;

    /**
     * If {@code true}, filters out messages not targeted to this client or tracker.
     */
    boolean filterMessageTarget;

    /**
     * If {@code true}, disables segment-based filtering, allowing access to all segments.
     */
    boolean ignoreSegment;

    /**
     * If {@code true}, assumes this is the only tracker for the consumer.
     */
    boolean singleTracker;

    /**
     * If {@code true}, indicates the client manages its own position/index.
     */
    boolean clientControlledIndex;

    /**
     * The last known index from which to continue reading.
     */
    Long lastIndex;

    /**
     * Optional timeout (in milliseconds) after which the tracker is purged if inactive.
     */
    Long purgeTimeout;

    /**
     * @return {@code true} if messages should not be filtered by target client/tracker ID. This is the inverse of
     * {@link #filterMessageTarget}.
     */
    @SuppressWarnings("unused")
    public boolean isIgnoreMessageTarget() {
        return !filterMessageTarget;
    }
}
