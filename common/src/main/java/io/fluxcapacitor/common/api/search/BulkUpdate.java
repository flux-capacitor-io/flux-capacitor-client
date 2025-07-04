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

package io.fluxcapacitor.common.api.search;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocumentIfNotExists;
import lombok.NonNull;

/**
 * Represents a single operation in a bulk document update.
 * <p>
 * Bulk updates are used to efficiently apply multiple indexing or deletion operations to a document store
 * in a single request. Implementations include:
 * <ul>
 *   <li>{@link IndexDocument} – to add or replace a document</li>
 *   <li>{@link IndexDocumentIfNotExists} – to add a document only if it doesn't exist</li>
 *   <li>{@link DeleteDocument} – to remove a document by ID and collection</li>
 * </ul>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({@Type(IndexDocument.class), @Type(IndexDocumentIfNotExists.class), @Type(DeleteDocument.class)})
public interface BulkUpdate {

    /**
     * The unique identifier of the document within the collection.
     */
    @NonNull String getId();

    /**
     * The name of the collection to which this document belongs.
     */
    @NonNull Object getCollection();

    /**
     * Indicates the operation type: {@code index}, {@code indexIfNotExists}, or {@code delete}.
     */
    Type getType();

    /**
     * Supported bulk operation types.
     */
    enum Type {
        index, indexIfNotExists, delete
    }
}
