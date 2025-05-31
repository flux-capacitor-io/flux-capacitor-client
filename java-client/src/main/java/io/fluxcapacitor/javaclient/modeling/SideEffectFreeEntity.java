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
import io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.toList;

/**
 * A functional, non-persistent {@link Entity} wrapper that enables safe state mutation and validation without producing
 * side effects such as event publication or snapshot creation.
 * <p>
 * {@code SideEffectFreeEntity} allows invocation of methods like {@link #apply(Message)},
 * {@link #update(UnaryOperator)}, and {@link #assertLegal(Object)}, making it suitable for scenarios where domain logic
 * or legality checks are needed without committing the resulting changes.
 * <p>
 * This entity is returned from {@link AggregateRepository#asEntity(Object)} or
 * {@link io.fluxcapacitor.javaclient.FluxCapacitor#asEntity(Object)} to allow ad-hoc interaction with domain objects
 * outside of the lifecycle of an active aggregate. It is particularly useful for:
 * <ul>
 *   <li>Performing validations with {@code @AssertLegal}</li>
 *   <li>Executing {@code @Apply} handlers to derive a new state</li>
 *   <li>Analyzing projected changes without actually modifying persisted state</li>
 * </ul>
 * Internally, it enforces {@link Entity#isLoading()} to remain {@code true} during {@code apply()} and {@code update()}
 * invocations to suppress unintended side effects like event publication or committing.
 * However, the returned state reflects the result of applying the logic, allowing chaining or inspection.
 * <p>
 * Unlike {@link NoOpEntity}, which disables all state changes entirely, {@code SideEffectFreeEntity} evaluates them
 * fully but simply discards any side effects. The {@link #commit()} operation is a no-op.
 *
 * @param <T> the type of the entity's value
 * @see NoOpEntity
 * @see ModifiableAggregateRoot
 * @see io.fluxcapacitor.javaclient.FluxCapacitor#asEntity(Object)
 */
@AllArgsConstructor
@Accessors(fluent = true)
public class SideEffectFreeEntity<T> implements Entity<T> {

    private final Supplier<Entity<T>> loader;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Entity<T> delegate = loader.get();

    @Getter(lazy = true)
    private final List<Entity<?>> entities
            = delegate().entities().stream().map(e -> new SideEffectFreeEntity<>((Entity<?>) e)).collect(toList());

    public SideEffectFreeEntity(Entity<T> delegate) {
        this.loader = () -> delegate;
    }

    @Override
    public SideEffectFreeEntity<T> apply(Message eventMessage) {
        var wasLoading = Entity.isLoading();
        try {
            Entity.loading.set(true);
            return new SideEffectFreeEntity<>(delegate().apply(eventMessage));
        } finally {
            Entity.loading.set(wasLoading);
        }
    }

    @Override
    public SideEffectFreeEntity<T> update(UnaryOperator<T> function) {
        var wasLoading = Entity.isLoading();
        try {
            Entity.loading.set(true);
            return new SideEffectFreeEntity<>(delegate().update(function));
        } finally {
            Entity.loading.set(wasLoading);
        }
    }

    @Override
    public Entity<T> commit() {
        return this;
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object update) throws E {
        delegate().assertLegal(update);
        return this;
    }

    @Override
    public Entity<?> parent() {
        return Optional.ofNullable(delegate().parent()).map(SideEffectFreeEntity::new).orElse(null);
    }

    @Override
    public Collection<?> aliases() {
        return delegate().aliases();
    }

    @Override
    public SideEffectFreeEntity<T> previous() {
        Entity<T> previous = delegate().previous();
        return previous == null ? null : new SideEffectFreeEntity<>(previous);
    }

    @Override
    public Object id() {
        return delegate().id();
    }

    @Override
    public Class<T> type() {
        return delegate().type();
    }

    @Override
    public Entity<T> withType(Class<T> type) {
        return delegate().withType(type);
    }

    @Override
    public T get() {
        return delegate().get();
    }

    @Override
    public String idProperty() {
        return delegate().idProperty();
    }
}
