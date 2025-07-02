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

import io.fluxcapacitor.common.api.RequestResult;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

import java.util.List;

/**
 * Result for a {@link io.fluxcapacitor.common.api.tracking.ReadFromIndex} request.
 * <p>
 * Contains the messages read from the specified index and accompanying metadata for auditing and metrics logging.
 */
@Value
public class ReadFromIndexResult implements RequestResult {

    /**
     * The request ID associated with the original {@code ReadFromIndex} request.
     */
    long requestId;

    /**
     * The list of serialized messages returned from the log starting at the requested index.
     */
    List<SerializedMessage> messages;

    /**
     * The system timestamp (epoch millis) at which this result was produced.
     */
    long timestamp = System.currentTimeMillis();

    /**
     * Converts this result into a metric representation, which includes the number of messages and timestamp.
     */
    @Override
    public Metric toMetric() {
        return new Metric(messages.size(), messages.stream().mapToLong(SerializedMessage::getBytes).sum(), timestamp);
    }

    /**
     * Metric representation of a {@code ReadFromIndexResult}, used for internal monitoring and analytics.
     */
    @Value
    public static class Metric {

        /**
         * The number of messages included in the result.
         */
        int size;

        /**
         * The total number of bytes in the message payloads.
         */
        long bytes;

        /**
         * The timestamp at which the result was generated.
         */
        long timestamp;
    }
}
