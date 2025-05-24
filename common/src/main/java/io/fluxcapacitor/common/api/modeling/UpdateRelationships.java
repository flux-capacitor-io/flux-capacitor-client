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
import java.util.stream.Stream;

/**
 * Command to inform the Flux platform about changes in entity-aggregate relationships.
 * <p>
 * These relationships allow the platform to determine which aggregate contains a given entity.
 * This is crucial when targeting nested entities with commands or when appending resulting events
 * to the appropriate aggregate.
 */
@Value
public class UpdateRelationships extends Command {

    /**
     * A set of new relationships between entities and aggregates that were just created or reinforced.
     */
    Set<Relationship> associations;

    /**
     * A set of relationships that are no longer valid or were explicitly removed.
     */
    Set<Relationship> dissociations;

    /**
     * Guarantees storage semantics such as durability for this update (e.g., STORED, NONE).
     */
    Guarantee guarantee;

    /**
     * Returns a routing key to optimize dispatching. Uses the first available aggregate ID
     * from the set of associations or dissociations.
     */
    @Override
    public String routingKey() {
        return Stream.concat(associations.stream(), dissociations.stream())
                .map(Relationship::getAggregateId)
                .findFirst()
                .orElse(null);
    }
}
