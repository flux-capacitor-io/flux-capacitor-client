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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.time.Instant;
import java.util.Collection;
import java.util.function.UnaryOperator;

/**
 * A base implementation of {@link Entity} that forwards all method calls to a delegated {@code Entity} instance.
 * <p>
 * {@code DelegatingEntity} is designed to serve as a foundation for wrapper implementations that enrich or modify
 * entity behavior without altering the core entity logic. It simplifies the creation of decorator-style entities
 * such as {@link ModifiableEntity}, {@link SideEffectFreeEntity}, and {@link ModifiableAggregateRoot}, allowing
 * selective method overriding while relying on delegation for the rest.
 * <p>
 * Subclasses typically override behavior like {@link #update(UnaryOperator)}, {@link #apply(Message)},
 * or {@link #assertLegal(Object)} to add custom lifecycle behavior (e.g., interception, queuing, validation)
 * while maintaining compatibility with the {@link Entity} interface.
 * <p>
 * By default, all methods delegate directly to the wrapped {@code Entity} instance, which is exposed via the
 * {@code #getDelegate()} accessor.
 *
 * @param <T> the type of the entity's value
 *
 * @see Entity
 * @see ModifiableEntity
 * @see ModifiableAggregateRoot
 * @see SideEffectFreeEntity
 */
@ToString
public abstract class DelegatingEntity<T> implements Entity<T> {
    @ToString.Include
    @EqualsAndHashCode.Include
    @Getter
    protected Entity<T> delegate;

    public DelegatingEntity(@NonNull Entity<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object id() {
        return delegate.id();
    }

    @Override
    public Collection<?> aliases() {
        return delegate.aliases();
    }

    @Override
    public Class<T> type() {
        return delegate.type();
    }

    @Override
    public T get() {
        return delegate.get();
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

    @Override
    public Instant timestamp() {
        return delegate.timestamp();
    }

    @Override
    public long sequenceNumber() {
        return delegate.sequenceNumber();
    }

    @Override
    public Entity<T> previous() {
        return delegate.previous();
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return delegate.entities();
    }

    @Override
    public Entity<?> parent() {
        return delegate.parent();
    }

    @Override
    public Entity<T> withEventIndex(Long index, String messageId) {
        return delegate.withEventIndex(index, messageId);
    }

    @Override
    public Entity<T> withType(Class<T> type) {
        return delegate.withType(type);
    }

    @Override
    public Entity<T> withSequenceNumber(long sequenceNumber) {
        return delegate.withSequenceNumber(sequenceNumber);
    }
}
