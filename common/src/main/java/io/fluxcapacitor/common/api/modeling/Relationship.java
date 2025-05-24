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

import lombok.Builder;
import lombok.Value;

/**
 * Describes a relationship between an entity and the aggregate that contains or owns it.
 * <p>
 * Relationships are essential for resolving which aggregate instance a given entity belongs to.
 * This enables developers to:
 * <ul>
 *   <li>Target a specific child entity in a command</li>
 *   <li>Automatically load that entity as part of the aggregate during command handling</li>
 *   <li>Ensure that any new events resulting from the command are appended to the correct aggregate</li>
 * </ul>
 *
 * <p>
 * These relationships are maintained automatically by the Flux platform for entity structures
 * annotated with {@code @Member}, but they can also be managed explicitly using commands like
 * {@link UpdateRelationships} or {@link RepairRelationships}.
 *
 * @see UpdateRelationships
 * @see RepairRelationships
 */
@Value
@Builder(toBuilder = true)
public class Relationship {
    /**
     * The ID of the aggregate that contains the entity.
     */
    String aggregateId;

    /**
     * The type or class name of the aggregate.
     */
    String aggregateType;

    /**
     * The ID of the entity that is part of the aggregate.
     */
    String entityId;
}
