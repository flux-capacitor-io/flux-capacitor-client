/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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
import io.fluxcapacitor.common.api.eventsourcing.*;
import io.fluxcapacitor.common.api.keyvalue.*;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.api.publishing.AppendAction;
import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.CancelScheduleAction;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.scheduling.ScheduleAction;
import io.fluxcapacitor.common.api.tracking.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        //publishing
        @JsonSubTypes.Type(value=Append.class, name="append"),
        @JsonSubTypes.Type(value=AppendAction.class, name="appendAction"),

        //tracking
        @JsonSubTypes.Type(value=Read.class, name="read"),
        @JsonSubTypes.Type(value=ReadResult.class, name="readResult"),
        @JsonSubTypes.Type(value=ReadAction.class, name="readAction"),
        @JsonSubTypes.Type(value=StorePosition.class, name="storePosition"),
        @JsonSubTypes.Type(value=StorePositionAction.class, name="storePositionAction"),

        //event sourcing
        @JsonSubTypes.Type(value=AppendEvents.class, name="appendEvents"),
        @JsonSubTypes.Type(value=AppendEventsAction.class, name="appendEventsAction"),
        @JsonSubTypes.Type(value=GetEvents.class, name="getEvents"),
        @JsonSubTypes.Type(value=GetEventsAction.class, name="getEventsAction"),
        @JsonSubTypes.Type(value=GetEventsResult.class, name="getEventsResult"),

        //scheduling
        @JsonSubTypes.Type(value=Schedule.class, name="schedule"),
        @JsonSubTypes.Type(value=ScheduleAction.class, name="scheduleAction"),
        @JsonSubTypes.Type(value=CancelSchedule.class, name="cancelSchedule"),
        @JsonSubTypes.Type(value=CancelScheduleAction.class, name="cancelScheduleAction"),

        //key-value
        @JsonSubTypes.Type(value=StoreValues.class, name="storeValues"),
        @JsonSubTypes.Type(value=StoreValuesAction.class, name="storeValuesAction"),
        @JsonSubTypes.Type(value=GetValue.class, name="getValue"),
        @JsonSubTypes.Type(value=GetValueAction.class, name="getValueAction"),
        @JsonSubTypes.Type(value=GetValueResult.class, name="getValueResult"),
        @JsonSubTypes.Type(value=DeleteValue.class, name="deleteValue"),
        @JsonSubTypes.Type(value=DeleteValueAction.class, name="deleteValueAction"),
})
public interface JsonType {
}
