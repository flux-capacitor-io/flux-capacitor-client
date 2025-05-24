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

import io.fluxcapacitor.common.api.RequestResult;
import lombok.Value;

import java.util.List;

/**
 * Response to a {@link GetRelationships} request, containing all known relationships for a given entity ID.
 * <p>
 * This includes all aggregates that reference or embed the specified entity, along with the entity ID used
 * in the relationship.
 *
 * @see GetRelationships
 * @see Relationship
 */
@Value
public class GetRelationshipsResult implements RequestResult {

    /**
     * Unique identifier linking this result to the originating {@link GetRelationships} request.
     */
    long requestId;

    /**
     * List of {@link Relationship} instances that reference the given entity ID across one or more aggregates.
     */
    List<Relationship> relationships;

    /**
     * Timestamp indicating when this result was created.
     */
    long timestamp = System.currentTimeMillis();
}