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

@Value
public class SerializedSchedule {
    String scheduleId;
    long timestamp;
    SerializedMessage message;
    boolean ifAbsent;

    @JsonIgnore
    public Metric toMetric() {
        return new Metric(scheduleId, timestamp, ifAbsent);
    }

    @Value
    public static class Metric {
        String scheduleId;
        long timestamp;
        boolean ifNotExists;
    }
}
