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

import io.fluxcapacitor.common.api.RequestResult;
import lombok.Value;

/**
 * Response to a {@link GetSchedule} request, containing the matching {@link SerializedSchedule}, if found.
 */
@Value
public class GetScheduleResult implements RequestResult {
    /**
     * The identifier of the request this result corresponds to.
     */
    long requestId;

    /**
     * The retrieved schedule, or {@code null} if no schedule exists for the given ID.
     */
    SerializedSchedule schedule;

    /**
     * The system time when this result was generated (milliseconds since epoch).
     */
    long timestamp = System.currentTimeMillis();

    @Override
    public Object toMetric() {
        return new Metric(schedule.getScheduleId(), schedule.getMessage().bytes());
    }

    /**
     * Metric payload used for internal monitoring and logging.
     */
    @Value
    public static class Metric {
        String scheduleId;
        long bytes;
    }
}