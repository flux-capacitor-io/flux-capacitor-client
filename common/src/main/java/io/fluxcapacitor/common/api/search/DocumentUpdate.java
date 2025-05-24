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

import lombok.Builder;
import lombok.Value;

/**
 * Represents a single low-level document update operation to be applied to a search collection.
 * <p>
 * This class is used internally by the Flux client when performing bulk update operations using
 * {@link io.fluxcapacitor.common.api.search.BulkUpdateDocuments}, which is sent to the Flux platform to apply
 * multiple updates in a single request.
 * <p>
 * The type of update is specified via {@link #type}, and may be:
 * <ul>
 *   <li>{@link io.fluxcapacitor.common.api.search.BulkUpdate.Type#index} – to unconditionally index the document</li>
 *   <li>{@link io.fluxcapacitor.common.api.search.BulkUpdate.Type#indexIfNotExists} – to index the document only if it does not already exist</li>
 *   <li>{@link io.fluxcapacitor.common.api.search.BulkUpdate.Type#delete} – to delete the document</li>
 * </ul>
 *
 * @see io.fluxcapacitor.common.api.search.BulkUpdateDocuments
 * @see io.fluxcapacitor.common.api.search.BulkUpdate
 * @see io.fluxcapacitor.common.api.search.SerializedDocument
 */
@Value
@Builder(builderClassName = "Builder")
public class DocumentUpdate {

    /**
     * The type of update to apply (index, index-if-not-exists, or delete).
     */
    BulkUpdate.Type type;

    /**
     * The ID of the document to update.
     */
    String id;

    /**
     * The name of the collection to which the document belongs.
     */
    String collection;

    /**
     * The serialized document to be indexed. May be {@code null} in case of deletions.
     */
    SerializedDocument object;
}
