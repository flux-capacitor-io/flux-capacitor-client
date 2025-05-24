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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.time.Instant;

import static io.fluxcapacitor.common.api.search.BulkUpdate.Type.indexIfNotExists;

/**
 * A bulk update operation that indexes a document only if it does not already exist in the store.
 * <p>
 * Useful to ensure immutability or to avoid overwriting existing entries.
 */
@Value
@AllArgsConstructor
@Builder(toBuilder = true)
public class IndexDocumentIfNotExists implements BulkUpdate {

    /**
     * The document to index, if not already present.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @NonNull Object object;

    /**
     * The document's unique ID within the collection.
     */
    String id;

    /**
     * The collection name to which this document belongs.
     */
    Object collection;

    /**
     * The optional start timestamp of the document.
     */
    Instant timestamp;

    /**
     * The optional end timestamp of the document.
     */
    Instant end;

    @Override
    public BulkUpdate.Type getType() {
        return indexIfNotExists;
    }
}
