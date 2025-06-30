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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;
import lombok.With;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a batch of messages retrieved from the message store for a specific segment range.
 * <p>
 * A message batch contains a list of {@link SerializedMessage} instances and metadata such as the last known index and
 * the {@link Position} of the consumer when this batch was read.
 * <p>
 * The {@code segment} and {@code position} fields are especially relevant when the tracker operates with
 * {@code ignoreSegment=true}, meaning segment filtering is handled on the client side. In such cases, the platform
 * returns all potentially matching messages, and the client uses the {@link Position} and {@code segment} range to
 * determine which messages are truly relevant for processing.
 */
@Value
public class MessageBatch {

    /**
     * The segment range this batch belongs to, expressed as a two-element array {@code [start, end]}.
     * <p>
     * This corresponds to the segment(s) currently claimed by the tracker.
     */
    int[] segment;

    /**
     * The list of messages in this batch.
     */
    @With
    List<SerializedMessage> messages;

    /**
     * The highest message index included in this batch. This may be {@code null} if the batch is empty.
     */
    Long lastIndex;

    /**
     * The consumer's position at the time of reading this batch. Used to resume reading or checkpoint progress.
     */
    Position position;

    /**
     * Indicates whether this message batch is empty.
     */
    @JsonIgnore
    public boolean isEmpty() {
        return messages.isEmpty();
    }

    /**
     * Returns the number of messages contained in this batch.
     */
    @JsonIgnore
    public int getSize() {
        return messages.size();
    }

    /**
     * Calculates the total number of bytes in the data across all messages within the batch.
     */
    @JsonIgnore
    public long getBytes() {
        return messages.stream().mapToLong(m -> m.getData().getValue().length).sum();
    }

    @Override
    public String toString() {
        return "MessageBatch{" +
               "segment=" + Arrays.toString(segment) +
               ", lastIndex=" + lastIndex +
               ", message count=" + messages.size() +
               '}';
    }

    /**
     * Converts this batch into a compact, serializable representation for monitoring purposes.
     *
     * @return a {@link Metric} representing key metadata about this batch
     */
    @JsonIgnore
    public Metric toMetric() {
        return new Metric(segment, getSize(), getBytes(), lastIndex, position);
    }

    /**
     * Compact summary of a {@link MessageBatch}, used for logging and performance tracking.
     */
    @Value
    public static class Metric {
        /**
         * The segment range from which the messages were retrieved.
         */
        int[] segment;

        /**
         * The number of messages in the batch.
         */
        int size;

        /**
         * The total number of bytes in the data across all messages within the batch.
         */
        long bytes;

        /**
         * The last index of the batch, used for progress tracking.
         */
        Long lastIndex;

        /**
         * The consumer's position when this batch was read.
         */
        Position position;
    }
}
