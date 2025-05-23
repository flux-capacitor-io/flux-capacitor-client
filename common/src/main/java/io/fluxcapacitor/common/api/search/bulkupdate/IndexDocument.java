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

package io.fluxcapacitor.common.api.search.bulkupdate;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.search.BulkUpdate;
import io.fluxcapacitor.common.api.search.SerializedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.time.Instant;
import java.util.Optional;

import static io.fluxcapacitor.common.api.search.BulkUpdate.Type.index;

@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class IndexDocument implements BulkUpdate {
    public static IndexDocument fromDocument(SerializedDocument document) {
        return new IndexDocument(document, document.getId(), document.getCollection(),
                                 Optional.ofNullable(document.getTimestamp()).map(Instant::ofEpochMilli).orElse(null),
                                 Optional.ofNullable(document.getEnd()).map(Instant::ofEpochMilli).orElse(null));
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @NonNull
    Object object;
    String id;
    Object collection;
    Instant timestamp;
    Instant end;

    @Override
    public BulkUpdate.Type getType() {
        return index;
    }
}
