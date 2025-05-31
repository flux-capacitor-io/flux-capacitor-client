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

package io.fluxcapacitor.javaclient.modeling;

import java.time.Instant;

/**
 * Represents the root of an aggregate in a domain model.
 * <p>
 * An {@code AggregateRoot} is a specialized {@link Entity} that serves as the entry point for a consistency boundary
 * in domain-driven design.
 * <p>
 * Unlike nested entities, an {@code AggregateRoot}'s {@link #parent()} is always {@code null}, as it is the top-level
 * context for its child entities.
 * <p>
 * {@link #previous()} can be used to access the prior version of the aggregate, enabling differential processing.
 *
 * @param <T> the type of the underlying domain object
 *
 * @see Entity
 * @see Aggregate
 */
public interface AggregateRoot<T> extends Entity<T> {

    @Override
    default Entity<?> parent() {
        return null;
    }

    @Override
    String lastEventId();

    @Override
    Long lastEventIndex();

    @Override
    Entity<T> withEventIndex(Long index, String messageId);

    @Override
    long sequenceNumber();

    @Override
    Entity<T> withSequenceNumber(long sequenceNumber);

    @Override
    Instant timestamp();

    @Override
    Entity<T> previous();


}
