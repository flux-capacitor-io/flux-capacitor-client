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

package io.fluxcapacitor.common.api.eventsourcing;

import io.fluxcapacitor.common.api.Request;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public class AppendEvents extends Request {
    List<EventBatch> eventBatches;

    @Override
    public String toString() {
        return "AppendEvents of size " + eventBatches.size();
    }

    @Override
    public Metric toMetric() {
        return new Metric(eventBatches.stream().map(EventBatch::toMetric).collect(Collectors.toList()));
    }

    @Value
    public static class Metric {
        List<EventBatch.Metric> eventBatches;
    }
}
