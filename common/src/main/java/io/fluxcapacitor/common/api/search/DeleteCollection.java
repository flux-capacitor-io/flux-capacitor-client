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
 * Command to delete an entire search collection, removing all documents within it.
 * <p>
 * This operation is irreversible and should be used with caution. It is useful in test environments
 * or in cases where all data in a collection has become obsolete and needs to be cleared.
 * <p>
 * Deletion behavior is governed by the provided {@link Guarantee}.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public class DeleteCollection extends Command {

    /**
     * The name of the collection to delete.
     */
    String collection;

    /**
     * The delivery/storage guarantee applied to the deletion operation.
     */
    Guarantee guarantee;

    /**
     * Uses the collection name as the routing key for consistency and traceability.
     */
    @Override
    public String routingKey() {
        return collection;
    }
}
