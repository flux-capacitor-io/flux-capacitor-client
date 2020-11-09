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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.eventsourcing.AppendEvents;
import io.fluxcapacitor.common.api.eventsourcing.AppendEventsEvent;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEvents;
import io.fluxcapacitor.common.api.eventsourcing.DeleteEventsEvent;
import io.fluxcapacitor.common.api.eventsourcing.GetEvents;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsEvent;
import io.fluxcapacitor.common.api.eventsourcing.GetEventsResult;
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.DeleteValueEvent;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueEvent;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.StoreValueIfAbsent;
import io.fluxcapacitor.common.api.keyvalue.StoreValueIfAbsentEvent;
import io.fluxcapacitor.common.api.keyvalue.StoreValues;
import io.fluxcapacitor.common.api.keyvalue.StoreValuesAndWait;
import io.fluxcapacitor.common.api.keyvalue.StoreValuesEvent;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.api.publishing.AppendEvent;
import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.CancelScheduleEvent;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.scheduling.ScheduleEvent;
import io.fluxcapacitor.common.api.tracking.DisconnectTracker;
import io.fluxcapacitor.common.api.tracking.DisconnectTrackerEvent;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadEvent;
import io.fluxcapacitor.common.api.tracking.ReadFromIndex;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexEvent;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexResult;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.ResetPosition;
import io.fluxcapacitor.common.api.tracking.ResetPositionEvent;
import io.fluxcapacitor.common.api.tracking.StorePosition;
import io.fluxcapacitor.common.api.tracking.StorePositionEvent;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        //common
        @JsonSubTypes.Type(value = VoidResult.class, name = "void"),
        @JsonSubTypes.Type(value = BooleanResult.class, name = "boolean"),
        @JsonSubTypes.Type(value = ConnectEvent.class, name = "connectEvent"),
        @JsonSubTypes.Type(value = DisconnectEvent.class, name = "disconnectEvent"),

        //publishing
        @JsonSubTypes.Type(value = Append.class, name = "append"),
        @JsonSubTypes.Type(value = AppendEvent.class, name = "appendEvent"),

        //tracking
        @JsonSubTypes.Type(value = Read.class, name = "read"),
        @JsonSubTypes.Type(value = ReadResult.class, name = "readResult"),
        @JsonSubTypes.Type(value = ReadEvent.class, name = "readEvent"),
        @JsonSubTypes.Type(value = StorePosition.class, name = "storePosition"),
        @JsonSubTypes.Type(value = StorePositionEvent.class, name = "storePositionEvent"),
        @JsonSubTypes.Type(value = ResetPosition.class, name = "resetPosition"),
        @JsonSubTypes.Type(value = ResetPositionEvent.class, name = "resetPositionEvent"),
        @JsonSubTypes.Type(value = DisconnectTracker.class, name = "disconnectTracker"),
        @JsonSubTypes.Type(value = DisconnectTrackerEvent.class, name = "disconnectTrackerEvent"),
        @JsonSubTypes.Type(value = ReadFromIndex.class, name = "readFromIndex"),
        @JsonSubTypes.Type(value = ReadFromIndexResult.class, name = "readFromIndexResult"),
        @JsonSubTypes.Type(value = ReadFromIndexEvent.class, name = "readFromIndexEvent"),

        //event sourcing
        @JsonSubTypes.Type(value = AppendEvents.class, name = "appendEvents"),
        @JsonSubTypes.Type(value = AppendEventsEvent.class, name = "appendEventsEvent"),
        @JsonSubTypes.Type(value = GetEvents.class, name = "getEvents"),
        @JsonSubTypes.Type(value = GetEventsEvent.class, name = "getEventsEvent"),
        @JsonSubTypes.Type(value = GetEventsResult.class, name = "getEventsResult"),
        @JsonSubTypes.Type(value = DeleteEvents.class, name = "deleteEvents"),
        @JsonSubTypes.Type(value = DeleteEventsEvent.class, name = "deleteEventsEvent"),

        //scheduling
        @JsonSubTypes.Type(value = Schedule.class, name = "schedule"),
        @JsonSubTypes.Type(value = ScheduleEvent.class, name = "scheduleEvent"),
        @JsonSubTypes.Type(value = CancelSchedule.class, name = "cancelSchedule"),
        @JsonSubTypes.Type(value = CancelScheduleEvent.class, name = "cancelScheduleEvent"),

        //key-value
        @JsonSubTypes.Type(value = StoreValues.class, name = "storeValues"),
        @JsonSubTypes.Type(value = StoreValuesEvent.class, name = "storeValuesEvent"),
        @JsonSubTypes.Type(value = StoreValuesAndWait.class, name = "storeValuesAndWaitEvent"),
        @JsonSubTypes.Type(value = GetValue.class, name = "getValue"),
        @JsonSubTypes.Type(value = GetValueEvent.class, name = "getValueEvent"),
        @JsonSubTypes.Type(value = GetValueResult.class, name = "getValueResult"),
        @JsonSubTypes.Type(value = DeleteValue.class, name = "deleteValue"),
        @JsonSubTypes.Type(value = DeleteValueEvent.class, name = "deleteValueEvent"),
        @JsonSubTypes.Type(value = StoreValueIfAbsent.class, name = "storeValueIfAbsent"),
        @JsonSubTypes.Type(value = StoreValueIfAbsentEvent.class, name = "storeValueIfAbsentEvent"),
})
public interface JsonType {
}
