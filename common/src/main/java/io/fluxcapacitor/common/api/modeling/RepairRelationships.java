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

package io.fluxcapacitor.common.api.modeling;

import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.Command;
import lombok.Value;

import java.util.Set;

/**
 * Command to repair or re-index the relationships for an existing aggregate.
 * <p>
 * This is particularly useful when an aggregate model evolves — for example, when child entity annotations like
 * {@code @Member} are introduced or corrected. It ensures that future commands targeting those entities can still
 * resolve the correct aggregate and maintain event consistency.
 *
 * <p>
 * Note: This command is intended for administrative use and is not normally emitted during regular application flows.
 *
 * @see Relationship
 * @see UpdateRelationships
 */
@Value
public class RepairRelationships extends Command {

    /**
     * The unique identifier of the aggregate whose relationships need to be repaired.
     */
    String aggregateId;

    /**
     * The aggregate's type (e.g., class name or logical category).
     */
    String aggregateType;

    /**
     * The set of entity IDs that should be (re)linked to the specified aggregate.
     */
    Set<String> entityIds;

    /**
     * Guarantees storage semantics for this repair operation.
     */
    Guarantee guarantee;

    /**
     * Routing key derived from the aggregate ID to ensure consistent processing.
     */
    @Override
    public String routingKey() {
        return aggregateId;
    }
}
