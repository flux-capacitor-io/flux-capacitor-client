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

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Command to delete a single document from the search store by its collection and ID.
 * <p>
 * This provides a lightweight alternative to a query-based deletion when the document ID is known.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class DeleteDocumentById extends Command {

    /**
     * The collection that contains the document.
     */
    String collection;

    /**
     * The unique ID of the document to delete.
     */
    String id;

    /**
     * The guarantee that determines whether the deletion must be sent, acknowledged, or stored.
     */
    Guarantee guarantee;

    /**
     * Uses the document ID as the routing key, ensuring consistency during deletion.
     */
    @Override
    public String routingKey() {
        return id;
    }
}
