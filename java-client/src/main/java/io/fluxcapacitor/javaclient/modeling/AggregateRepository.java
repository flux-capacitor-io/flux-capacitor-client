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

package io.fluxcapacitor.javaclient.modeling;

import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourced;

public interface AggregateRepository {

    boolean supports(Class<?> aggregateType);

    boolean cachingAllowed(Class<?> aggregateType);

    /**
     * Loads the aggregate root of type {@code <T>} with given id.
     *
     * @see EventSourced for more info on how to define an event sourced aggregate root
     */
    default <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType) {
        return load(aggregateId, aggregateType, false, false);
    }

    <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean readOnly, boolean onlyCached);

}
