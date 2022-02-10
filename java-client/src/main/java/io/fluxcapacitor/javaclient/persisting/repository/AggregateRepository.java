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

import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ReadOnlyAggregateRoot;

import java.util.Optional;

public interface AggregateRepository {

    <T> AggregateRoot<T> load(String aggregateId, Class<T> aggregateType);

    <T> AggregateRoot<T> loadFor(String entityId, Class<?> entityType);

    default <T> AggregateRoot<T> load(String aggregateId, Class<T> type, boolean readOnly) {
        return Optional.of(load(aggregateId, type)).map(a -> readOnly ? new ReadOnlyAggregateRoot<>(a) : a).get();
    }

    boolean cachingAllowed(Class<?> aggregateType);

}