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

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import lombok.With;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * A lazily-loaded implementation of {@link AggregateRoot} that defers deserialization and event application until the
 * entity value is explicitly accessed.
 * <p>
 * This implementation is typically used as a historical {@code previous} reference in {@link ImmutableAggregateRoot}
 * when the aggregate's {@link io.fluxcapacitor.javaclient.modeling.Aggregate#cachingDepth()} has been exceeded. In
 * such cases, only the checkpointed state is retained in memory, and the full entity state is reconstructed by
 * replaying events from the most recent checkpoint.
 * <p>
 * {@code LazyAggregateRoot} is read-only and will throw {@link UnsupportedOperationException} for any attempt to
 * apply updates or modify the state. It is solely intended for retrieving past states in an efficient and
 * memory-conscious way.
 * <p>
 * Event replay occurs on demand via the {@link #get()} method, which reconstructs the aggregate state by re-applying
 * all events from the last known checkpoint until the desired sequence number or event id is reached.
 *
 * @param <T> The type of the aggregate's value.
 * @see ImmutableAggregateRoot#asPrevious(long)
 */
public class LazyAggregateRoot<T> implements AggregateRoot<T> {
    @With
    private final ImmutableAggregateRoot<T> delegate;

    public static <T> LazyAggregateRoot<T> from(ImmutableAggregateRoot<T> delegate) {
        return new LazyAggregateRoot<>(delegate.toBuilder().value(null).build());
    }

    protected LazyAggregateRoot(ImmutableAggregateRoot<T> delegate) {
        this.delegate = delegate;
        if (!rootAnnotation().eventSourced()) {
            throw new IllegalStateException("Cannot create lazy aggregate: event sourcing is disabled.");
        }
    }

    @Override
    public Entity<T> withEventIndex(Long index, String messageId) {
        return withDelegate((ImmutableAggregateRoot<T>) delegate.withEventIndex(index, messageId));
    }

    @Override
    public long sequenceNumber() {
        return delegate.sequenceNumber();
    }

    @Override
    public Instant timestamp() {
        return delegate.timestamp();
    }

    @Override
    public Entity<T> previous() {
        return delegate.previous();
    }

    /*
        Requires loading events
     */

    @Override
    public T get() {
        var start = getLastCheckpoint();
        String targetEventId = lastEventId();
        AggregateEventStream<DeserializingMessage> events = delegate.eventStore().getEvents(
                id(), start.sequenceNumber(), (int) (sequenceNumber() - start.sequenceNumber()),
                rootAnnotation().ignoreUnknownEvents());
        Iterator<DeserializingMessage> iterator = events.iterator();
        Entity<T> result = start;
        boolean eventReached = false;
        while (iterator.hasNext()) {
            DeserializingMessage nextEvent = iterator.next();
            boolean lastEventId = Objects.equals(targetEventId, nextEvent.getMessageId());
            if (eventReached && !lastEventId) {
                break;
            }
            if (lastEventId) {
                eventReached = true;
            }
            result = result.apply(nextEvent);
        }
        return result.get();
    }

    protected Entity<T> getLastCheckpoint() {
        Entity<T> result = previous();
        while (result instanceof LazyAggregateRoot<?>) {
            result = result.previous();
        }
        if (result == null) {
            throw new IllegalStateException("Failed to get last checkpoint for aggregate: " + id());
        }
        return result;
    }

    @Override
    public Collection<?> aliases() {
        return delegate.toBuilder().value(get()).build().aliases();
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return delegate.toBuilder().value(get()).build().entities();
    }

    /*
        Simply delegation
     */

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public Class<T> type() {
        return delegate.type();
    }

    @Override
    public String idProperty() {
        return delegate.idProperty();
    }

    @Override
    public String lastEventId() {
        return delegate.lastEventId();
    }

    @Override
    public Long lastEventIndex() {
        return delegate.lastEventIndex();
    }

    /*
        Unsupported operations
     */

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public Entity<T> apply(Message eventMessage) {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public Entity<T> commit() {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public Entity<T> withType(Class<T> type) {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public Entity<T> withSequenceNumber(long sequenceNumber) {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object update) throws E {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }
}
