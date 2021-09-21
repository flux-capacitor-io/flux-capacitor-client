/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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
import io.fluxcapacitor.common.api.keyvalue.DeleteValue;
import io.fluxcapacitor.common.api.keyvalue.GetValue;
import io.fluxcapacitor.common.api.keyvalue.GetValueResult;
import io.fluxcapacitor.common.api.keyvalue.StoreValueIfAbsent;
import io.fluxcapacitor.common.api.keyvalue.StoreValues;
import io.fluxcapacitor.common.api.keyvalue.StoreValuesAndWait;
import io.fluxcapacitor.common.api.publishing.Append;
import io.fluxcapacitor.common.api.scheduling.CancelSchedule;
import io.fluxcapacitor.common.api.scheduling.Schedule;
import io.fluxcapacitor.common.api.search.BulkUpdateDocuments;
import io.fluxcapacitor.common.api.search.CreateAuditTrail;
import io.fluxcapacitor.common.api.search.DeleteCollection;
import io.fluxcapacitor.common.api.search.DeleteDocumentById;
import io.fluxcapacitor.common.api.search.DeleteDocuments;
import io.fluxcapacitor.common.api.search.GetDocumentStats;
import io.fluxcapacitor.common.api.search.GetDocumentStatsResult;
import io.fluxcapacitor.common.api.search.GetDocumentsResult;
import io.fluxcapacitor.common.api.search.GetSearchHistogram;
import io.fluxcapacitor.common.api.search.GetSearchHistogramResult;
import io.fluxcapacitor.common.api.search.IndexDocuments;
import io.fluxcapacitor.common.api.search.SearchDocuments;
import io.fluxcapacitor.common.api.search.SearchDocumentsResult;
import io.fluxcapacitor.common.api.tracking.DisconnectTracker;
import io.fluxcapacitor.common.api.tracking.Read;
import io.fluxcapacitor.common.api.tracking.ReadFromIndex;
import io.fluxcapacitor.common.api.tracking.ReadFromIndexResult;
import io.fluxcapacitor.common.api.tracking.ReadResult;
import io.fluxcapacitor.common.api.tracking.ResetPosition;
import io.fluxcapacitor.common.api.tracking.StorePosition;




@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        //common
        @JsonSubTypes.Type(value = VoidResult.class, name = "void"),
        @JsonSubTypes.Type(value = BooleanResult.class, name = "boolean"),
        @JsonSubTypes.Type(value = StringResult.class, name = "string"),
        @JsonSubTypes.Type(value = ConnectEvent.class, name = "connectEvent"),
        @JsonSubTypes.Type(value = DisconnectEvent.class, name = "disconnectEvent"),
        @JsonSubTypes.Type(value = RequestBatch.class, name = "requestBatch"),
        @JsonSubTypes.Type(value = ResultBatch.class, name = "resultBatch"),

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

        //search
        @JsonSubTypes.Type(value = IndexDocuments.class, name = "indexDocuments"),
        @JsonSubTypes.Type(value = SearchDocuments.class, name = "searchDocuments"),
        @JsonSubTypes.Type(value = GetSearchHistogram.class, name = "getSearchHistogram"),
        @JsonSubTypes.Type(value = GetSearchHistogramResult.class, name = "getSearchHistogramResult"),
        @JsonSubTypes.Type(value = DeleteCollection.class, name = "deleteCollection"),
        @JsonSubTypes.Type(value = DeleteDocuments.class, name = "deleteDocuments"),
        @JsonSubTypes.Type(value = DeleteDocumentById.class, name = "deleteDocumentById"),
        @JsonSubTypes.Type(value = BulkUpdateDocuments.class, name = "bulkUpdateDocuments"),
        @JsonSubTypes.Type(value = GetDocumentStats.class, name = "getDocumentStats"),
        @JsonSubTypes.Type(value = SearchDocumentsResult.class, name = "searchDocumentsResult"),
        @JsonSubTypes.Type(value = GetDocumentsResult.class, name = "getDocumentsResult"),
        @JsonSubTypes.Type(value = GetDocumentStatsResult.class, name = "getDocumentStatsResult"),
        @JsonSubTypes.Type(value = CreateAuditTrail.class, name = "createAuditTrail"),
})
public interface JsonType {
    @JsonIgnore
    default Object toMetric(){
        return this;
    }
}
