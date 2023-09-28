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
import lombok.With;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.function.UnaryOperator;

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
        Iterator<DeserializingMessage> iterator = delegate.eventStore().getEvents(
                id(), start.sequenceNumber(), (int) (sequenceNumber() - start.sequenceNumber()),
                rootAnnotation().ignoreUnknownEvents()).iterator();
        Entity<T> result = start;
        while (iterator.hasNext()) {
            result = result.apply(iterator.next());
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
    public Entity<T> withType(Class<T> type) {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public Entity<T> withSequenceNumber(long sequenceNumber) {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object command) throws E {
        throw new UnsupportedOperationException("This aggregate is read-only.");
    }
}
