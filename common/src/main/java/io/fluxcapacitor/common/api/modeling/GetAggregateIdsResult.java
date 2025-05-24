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

import java.util.Map;

/**
 * Response to a {@link GetAggregateIds} request, returning a mapping of aggregate IDs to types.
 * <p>
 * The result indicates all aggregates that are associated with the provided entity ID.
 * This is particularly useful for routing messages or updates when only an entity reference is known.
 */
@Value
public class GetAggregateIdsResult implements RequestResult {
    /**
     * Unique identifier linking this result to its originating request.
     */
    long requestId;

    /**
     * A map where the key is the aggregate ID and the value is the corresponding aggregate type
     * that contains or is associated with the given entity ID.
     */
    Map<String, String> aggregateIds;

    /**
     * The timestamp at which this result was generated.
     */
    long timestamp = System.currentTimeMillis();
}