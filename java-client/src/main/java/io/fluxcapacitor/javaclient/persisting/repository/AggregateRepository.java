/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.Id;
import lombok.NonNull;

import java.util.Map;
import java.util.Optional;

public interface AggregateRepository {

    default <T> Entity<T> load(Id<T> aggregateId) {
        return load(aggregateId.toString(), aggregateId.getType());
    }

    <T> Entity<T> load(@NonNull Object aggregateId, Class<T> aggregateType);

    <T> Entity<T> loadFor(@NonNull Object entityId, Class<?> defaultType);

    default Awaitable repairRelationships(Id<?> aggregateId) {
        return repairRelationships(load(aggregateId));
    }

    default Awaitable repairRelationships(Object aggregateId) {
        return repairRelationships(load(aggregateId, Object.class));
    }

    Awaitable repairRelationships(Entity<?> aggregate);

    Map<String, Class<?>> getAggregatesFor(Object entityId);

    default Optional<String> getLatestAggregateId(Object entityId) {
        return getAggregatesFor(entityId).keySet().stream().reduce((a, b) -> b);
    }
}
