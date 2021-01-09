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

package io.fluxcapacitor.common.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.api.keyvalue.*;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.tracking.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        //common
        @JsonSubTypes.Type(value = VoidResult.class, name = "void"),
        @JsonSubTypes.Type(value = BooleanResult.class, name = "boolean"),
        @JsonSubTypes.Type(value = ConnectEvent.class, name = "connectEvent"),
        @JsonSubTypes.Type(value = DisconnectEvent.class, name = "disconnectEvent"),

        //publishing
        @JsonSubTypes.Type(value = Append.class, name = "append"),

        //tracking
        @JsonSubTypes.Type(value = Read.class, name = "read"),
        @JsonSubTypes.Type(value = ReadResult.class, name = "readResult"),
        @JsonSubTypes.Type(value = StorePosition.class, name = "storePosition"),
        @JsonSubTypes.Type(value = ResetPosition.class, name = "resetPosition"),
        @JsonSubTypes.Type(value = DisconnectTracker.class, name = "disconnectTracker"),
        @JsonSubTypes.Type(value = ReadFromIndex.class, name = "readFromIndex"),
        @JsonSubTypes.Type(value = ReadFromIndexResult.class, name = "readFromIndexResult"),

        //event sourcing
        @JsonSubTypes.Type(value = AppendEvents.class, name = "appendEvents"),
        @JsonSubTypes.Type(value = GetEvents.class, name = "getEvents"),
        @JsonSubTypes.Type(value = GetEventsResult.class, name = "getEventsResult"),
        @JsonSubTypes.Type(value = DeleteEvents.class, name = "deleteEvents"),

        //scheduling
        @JsonSubTypes.Type(value = Schedule.class, name = "schedule"),
        @JsonSubTypes.Type(value = CancelSchedule.class, name = "cancelSchedule"),

        //key-value
        @JsonSubTypes.Type(value = StoreValues.class, name = "storeValues"),
        @JsonSubTypes.Type(value = StoreValuesAndWait.class, name = "storeValuesAndWait"),
        @JsonSubTypes.Type(value = GetValue.class, name = "getValue"),
        @JsonSubTypes.Type(value = GetValueResult.class, name = "getValueResult"),
        @JsonSubTypes.Type(value = DeleteValue.class, name = "deleteValue"),
        @JsonSubTypes.Type(value = StoreValueIfAbsent.class, name = "storeValueIfAbsent"),
})
public interface JsonType {
    @JsonIgnore
    default Object toMetric(){
        return this;
    }
}
