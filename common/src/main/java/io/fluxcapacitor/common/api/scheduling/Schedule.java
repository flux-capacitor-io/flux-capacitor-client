/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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

import io.fluxcapacitor.common.api.JsonType;
import lombok.Value;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Value
@Deprecated(forRemoval = true)
public class Schedule implements JsonType {
    List<SerializedSchedule> messages;

    @Override
    public String toString() {
        return "Schedule of size " + messages.size();
    }

    @Override
    public Object toMetric() {
        return new Metric(messages.stream().map(SerializedSchedule::toMetric).collect(toList()), messages.size());
    }

    @Value
    public static class Metric {
        List<SerializedSchedule.Metric> messages;
        int size;
    }
}
