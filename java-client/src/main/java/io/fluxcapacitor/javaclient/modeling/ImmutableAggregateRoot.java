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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.javaclient.FluxCapacitor.currentTime;
import static java.util.Optional.ofNullable;

@Value
@NonFinal
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@Jacksonized
public class ImmutableAggregateRoot<T> extends ImmutableEntity<T> implements AggregateRoot<T> {
    @JsonProperty
    String lastEventId;
    @JsonProperty
    Long lastEventIndex;
    @JsonProperty
    @Builder.Default
    Instant timestamp = currentTime();
    @JsonProperty
    @Builder.Default
    long sequenceNumber = -1L;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Entity<T> previous;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Set<Relationship> relationships = super.relationships();

    transient EventStore eventStore;

    public static <T> ImmutableAggregateRoot<T> from(Entity<T> a, EntityHelper entityHelper, Serializer serializer,
                                                     EventStore eventStore) {
        return a == null ? null : ImmutableAggregateRoot.<T>builder()
                .entityHelper(entityHelper)
                .serializer(serializer)
                .id(a.id())
                .value(a.get())
                .type(a.type())
                .idProperty(a.idProperty())
                .lastEventId(a.lastEventId())
                .lastEventIndex(a.lastEventIndex())
                .timestamp(a.timestamp())
                .sequenceNumber(a.sequenceNumber())
                .eventStore(eventStore)
                .previous(ofNullable(from(a.previous(), entityHelper, serializer, eventStore))
                                  .map(p -> p.asPrevious(a.sequenceNumber())).orElse(null))
                .build();
    }

    public Entity<T> apply(DeserializingMessage message) {
        long newSequenceNumber = sequenceNumber() + 1L;
        return ((ImmutableAggregateRoot<T>) super.apply(message))
                .toBuilder()
                .previous(asPrevious(newSequenceNumber))
                .timestamp(message.getTimestamp())
                .lastEventId(message.getMessageId())
                .lastEventIndex(message.getIndex())
                .sequenceNumber(newSequenceNumber)
                .build();
    }

    Entity<T> asPrevious(long highestSequenceNumber) {
        if (rootAnnotation().cachingDepth() < 0 || !rootAnnotation().eventSourced() || !rootAnnotation().cached()
            || sequenceNumber() <= 0 || sequenceNumber() == highestSequenceNumber
            || rootAnnotation().checkpointPeriod() <= 1) {
            return this;
        }
        if (highestSequenceNumber - sequenceNumber() >= rootAnnotation().cachingDepth()
            && sequenceNumber() % rootAnnotation().checkpointPeriod() != 0) {
            return LazyAggregateRoot.from(this);
        }
        return previous() instanceof ImmutableAggregateRoot<T> p ?
                toBuilder().previous(p.asPrevious(highestSequenceNumber)).build() : this;
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        return ((ImmutableAggregateRoot<T>) super.update(function))
                .toBuilder()
                .timestamp(currentTime())
                .build();
    }

    @Override
    public Entity<T> withEventIndex(Long index, String messageId) {
        if (Objects.equals(messageId, lastEventId())) {
            return lastEventIndex() == null ? toBuilder().lastEventIndex(index).build() : this;
        }
        return toBuilder().previous(previous().withEventIndex(index, messageId)).build();
    }

    @Override
    public Entity<T> withSequenceNumber(long sequenceNumber) {
        return toBuilder().sequenceNumber(sequenceNumber).build();
    }
}
