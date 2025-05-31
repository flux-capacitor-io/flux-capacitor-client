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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.toList;

/**
 * A read-only, inert {@link Entity} wrapper used exclusively during the loading phase of aggregate state.
 * <p>
 * {@code NoOpEntity} is returned when an aggregate is accessed while {@link Entity#isLoading()} is {@code true},
 * such as during event sourcing or the initialization of other aggregates. This occurs for instance when
 * an aggregate is loaded from within another aggregate’s {@code @Apply} handler or any other mechanism
 * that recursively triggers loading.
 * <p>
 * This wrapper prevents any state mutation or validation during this phase by disabling:
 * <ul>
 *     <li>{@code apply()} – event reapplication is skipped</li>
 *     <li>{@code update()} – no structural or state changes are made</li>
 *     <li>{@code assertLegal()} – validations are suppressed</li>
 *     <li>{@code commit()} – no changes are queued or published</li>
 * </ul>
 * However, it allows safe navigation and inspection of the aggregate structure via:
 * <ul>
 *     <li>{@code get()}, {@code id()}, {@code type()}, {@code entities()}, {@code aliases()}, etc.</li>
 *     <li>Delegation to parent or previous versions (wrapped in {@code NoOpEntity})</li>
 * </ul>
 * <p>
 * This mechanism ensures that during loading, especially recursive or nested aggregate access,
 * no unintended updates are performed or committed. Outside of loading (i.e., when
 * {@link Entity#isLoading()} is {@code false}), such restrictions are lifted, and aggregates
 * may freely apply updates to other aggregates.
 *
 * @param <T> the type of the entity's value
 *
 * @see Entity#isLoading()
 * @see ModifiableAggregateRoot
 * @see ImmutableAggregateRoot
 */
@AllArgsConstructor
@Accessors(fluent = true)
public class NoOpEntity<T> implements Entity<T> {

    private final Supplier<Entity<T>> loader;

    @Getter(lazy = true, value = AccessLevel.PRIVATE)
    private final Entity<T> delegate = loader.get();

    private NoOpEntity(Entity<T> delegate) {
        this.loader = () -> delegate;
    }

    @Override
    public Entity<T> apply(Message eventMessage) {
        return this;
    }

    @Override
    public Entity<T> commit() {
        return this;
    }

    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        return this;
    }

    @Override
    public <E extends Exception> NoOpEntity<T> assertLegal(Object update) throws E {
        return this;
    }

    @Override
    public Entity<?> parent() {
        return Optional.ofNullable(delegate().parent()).map(NoOpEntity::new).orElse(null);
    }

    @Override
    public Collection<?> aliases() {
        return delegate().aliases();
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return delegate().entities().stream().map(e -> new NoOpEntity<>((Entity<?>) e)).collect(toList());
    }

    @Override
    public Entity<T> previous() {
        return new NoOpEntity<>(delegate().previous());
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
