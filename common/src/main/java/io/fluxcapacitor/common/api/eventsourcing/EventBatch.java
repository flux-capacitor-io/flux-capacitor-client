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

package io.fluxcapacitor.common.api.eventsourcing;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.SerializedMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
public class EventBatch {
    String aggregateId;
    List<SerializedMessage> events;
    boolean storeOnly;

    @JsonIgnore
    public boolean isEmpty() {
        return events.isEmpty();
    }

    @JsonIgnore
    public int getSize() {
        return events.size();
    }

    @Override
    public String toString() {
        return "EventBatch{" +
                "aggregateId='" + aggregateId + '\'' +
                ", event count=" + events.size() +
                ", storeOnly=" + storeOnly +
                '}';
    }

    @JsonIgnore
    public Metric toMetric() {
        return Metric.builder().aggregateId(aggregateId).size(events.size()).storeOnly(storeOnly).build();
    }

    @Value
    @Builder
    public static class Metric {
        String aggregateId;
        int size;
        boolean storeOnly;
    }
}
