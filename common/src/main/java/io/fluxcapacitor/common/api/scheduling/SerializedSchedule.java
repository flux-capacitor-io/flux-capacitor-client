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

package io.fluxcapacitor.common.api.scheduling;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Value;

/**
 * Represents a scheduled message to be delivered at a specific {@link #timestamp}.
 * <p>
 * If a schedule with the same {@link #scheduleId} already exists:
 * <ul>
 *   <li>It will be replaced by this schedule if {@link #ifAbsent} is {@code false} (default behavior).</li>
 *   <li>It will be preserved (this one ignored) if {@link #ifAbsent} is {@code true}.</li>
 * </ul>
 *
 * @see io.fluxcapacitor.common.api.scheduling.Schedule
 */
@Value
public class SerializedSchedule {
    /**
     * A unique identifier for this scheduled message. Used for deduplication or replacement.
     */
    String scheduleId;

    /**
     * The absolute timestamp (epoch millis) at which this message should be delivered.
     */
    long timestamp;

    /**
     * The message to be dispatched once the schedule triggers.
     */
    SerializedMessage message;

    /**
     * If {@code true}, the message is only scheduled if no schedule with the same {@link #scheduleId} exists.
     */
    boolean ifAbsent;

    /**
     * Returns a lightweight metric representation of this schedule for monitoring purposes.
     */
    @JsonIgnore
    public Metric toMetric() {
        return new Metric(scheduleId, timestamp, ifAbsent);
    }

    /**
     * Metric representation of the {@link SerializedSchedule} for tracking/monitoring.
     */
    @Value
    public static class Metric {
        String scheduleId;
        long timestamp;
        boolean ifNotExists;
    }
}
