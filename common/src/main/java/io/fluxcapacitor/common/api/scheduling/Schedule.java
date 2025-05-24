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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.Value;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Command to schedule a batch of messages for future delivery.
 * <p>
 * Each message in the {@link #messages} list is wrapped as a {@link SerializedSchedule}, which contains the payload,
 * metadata, and deadline. Messages will be automatically dispatched once their scheduled deadline is reached.
 * </p>
 *
 * <p>
 * Each scheduled message must have a unique {@code scheduleId}. If a schedule with the same ID already exists,
 * it will be <strong>replaced</strong> unless {@code ifAbsent in SerializedSchedule} is set to {@code true}, in which
 * case the existing schedule is preserved.
 *
 * <h2>Usage</h2>
 * Scheduling is typically done via higher level APIs in {@code FluxCapacitor} but can also be done using the
 * lower-level {@code SchedulingClient} that uses Flux Platform APIs.
 *
 * <h2>Handling</h2>
 * <p>
 * Scheduled messages are typically handled by {@code @HandleSchedule} methods at or near their {@code timestamp}.
 *
 * @see SerializedSchedule
 */
@Value
public class Schedule extends Command {
    /**
     * The list of messages to schedule.
     */
    List<SerializedSchedule> messages;

    /**
     * The delivery guarantee for scheduled messages (e.g. {@code SENT}, {@code STORED}).
     */
    Guarantee guarantee;

    @Override
    public String toString() {
        return "Schedule of size " + messages.size();
    }

    @Override
    public Object toMetric() {
        return new Metric(messages.stream().map(SerializedSchedule::toMetric).collect(toList()), messages.size());
    }

    /**
     * Lightweight metric representation of this {@code Schedule} command, used for monitoring purposes.
     */
    @Value
    public static class Metric {
        List<SerializedSchedule.Metric> messages;
        int size;
    }
}
