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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import io.fluxcapacitor.common.api.search.bulkupdate.DeleteDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocument;
import io.fluxcapacitor.common.api.search.bulkupdate.IndexDocumentIfNotExists;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Collection;

/**
 * Command to perform a batch update of documents in the search/document store.
 * <p>
 * This operation accepts a collection of {@link DocumentUpdate} items, which may be of the types:
 * <ul>
 *   <li>{@link IndexDocument} – index or replace a document</li>
 *   <li>{@link IndexDocumentIfNotExists} – index only if the document does not yet exist</li>
 *   <li>{@link DeleteDocument} – remove a document from a collection</li>
 * </ul>
 * <p>
 * The entire batch is processed together, and depending on the {@link Guarantee}, may ensure delivery or persistence.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class BulkUpdateDocuments extends Command {

    /**
     * A list of document update operations to perform in this batch.
     */
    Collection<DocumentUpdate> updates;

    /**
     * The delivery or storage guarantee for the update operation.
     */
    Guarantee guarantee;

    /**
     * @return the number of document updates in this batch.
     */
    @JsonIgnore
    public int getSize() {
        return updates.size();
    }

    @Override
    public String toString() {
        return "BulkUpdateDocuments of length " + updates.size();
    }

    /**
     * Metric representation of the bulk update, for logging or monitoring.
     */
    @Override
    public Object toMetric() {
        return new Metric(updates.size(), guarantee);
    }

    /**
     * Uses the ID of the first update as the routing key for this command.
     */
    @Override
    public String routingKey() {
        return updates.stream().map(DocumentUpdate::getId).findFirst().orElse(null);
    }

    /**
     * Metric representation for monitoring or observability.
     */
    @Value
    public static class Metric {
        int size;
        Guarantee guarantee;
    }
}
