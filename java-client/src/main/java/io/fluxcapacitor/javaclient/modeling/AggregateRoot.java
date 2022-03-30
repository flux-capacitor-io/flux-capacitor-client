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

import com.google.common.collect.Sets;
import io.fluxcapacitor.common.api.modeling.Relationship;

import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.String.format;

public interface AggregateRoot<T> extends Entity<AggregateRoot<T>, T> {

    ThreadLocal<Boolean> loading = ThreadLocal.withInitial(() -> false);

    static boolean isLoading() {
        return loading.get();
    }

    String AGGREGATE_ID_METADATA_KEY = "$aggregateId";
    String AGGREGATE_TYPE_METADATA_KEY = "$aggregateType";

    String lastEventId();

    Long lastEventIndex();

    Instant timestamp();

    long sequenceNumber();

    AggregateRoot<T> previous();

    default AggregateRoot<T> playBackToEvent(String eventId) {
        return playBackToCondition(aggregate -> Objects.equals(eventId, aggregate.lastEventId()))
                .orElseThrow(() -> new IllegalStateException(format(
                        "Could not load aggregate %s of type %s for event %s. Aggregate (%s) started at event %s",
                        id(), type().getSimpleName(), eventId, this, lastEventId())));
    }

    default Optional<AggregateRoot<T>> playBackToCondition(Predicate<AggregateRoot<T>> condition) {
        AggregateRoot<T> result = this;
        while (result != null && !condition.test(result)) {
            result = result.previous();
        }
        return Optional.ofNullable(result);
    }

    default AggregateRoot<T> makeReadOnly() {
        if (this instanceof ReadOnlyAggregateRoot<?>) {
            return this;
        }
        return new ReadOnlyAggregateRoot<>(this);
    }

    default Set<Relationship> relationships() {
        String id = id().toString();
        String type = type().getName();
        return get() == null ? Collections.emptySet()
                : allEntities().map(Entity::id).filter(Objects::nonNull)
                .map(entityId -> Relationship.builder().entityId(entityId.toString()).aggregateType(type)
                        .aggregateId(id).build())
                .collect(Collectors.toSet());
    }

    default Set<Relationship> associations(AggregateRoot<?> previous) {
        return Sets.difference(relationships(), previous.relationships());
    }

    default Set<Relationship> dissociations(AggregateRoot<?> previous) {
        return Sets.difference(previous.relationships(), relationships());
    }
}