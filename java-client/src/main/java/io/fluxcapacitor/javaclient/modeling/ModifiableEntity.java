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
import lombok.ToString;
import lombok.Value;

import java.util.Collection;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;

/**
 * A mutable view on a nested entity within a {@link ModifiableAggregateRoot}.
 * <p>
 * This class wraps a delegate {@link Entity} and maintains a reference to the root {@link ModifiableAggregateRoot},
 * enabling mutating operations—such as {@link #update(UnaryOperator)}, {@link #apply(Message)}, and
 * {@link #assertLegal(Object)}—to be routed through the root context.
 * <p>
 * This ensures that:
 * <ul>
 *     <li>All changes are tracked and committed through the root aggregate’s lifecycle.</li>
 *     <li>Relationship resolution and legality checks are coordinated at the aggregate level.</li>
 *     <li>Nested child entities participate in the aggregate’s commit process transparently.</li>
 * </ul>
 * <p>
 * The {@code ModifiableEntity} is typically returned when loading or navigating to a non-root entity
 * (i.e., a member or child entity) of an aggregate. Despite being indirectly updatable,
 * such child entities share the same mutation and commit infrastructure as the root.
 * <p>
 * Any invocation of {@code update()}, {@code apply()}, {@code assertAndApply()}, or {@code commit()}
 * will cause the root aggregate to manage the lifecycle and capture any applied events or state changes.
 *
 * @param <T> the type of the entity’s value
 * @see ModifiableAggregateRoot
 * @see DelegatingEntity
 */
@Value
@ToString(callSuper = true)
public class ModifiableEntity<T> extends DelegatingEntity<T> {
    public ModifiableEntity(Entity<T> delegate, ModifiableAggregateRoot<?> root) {
        super(delegate);
        this.root = root;
    }

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    ModifiableAggregateRoot<?> root;

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object update) throws E {
        root.assertLegal(update);
        return this;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        return (Entity<T>) ((Entity) root).update(
                r -> delegate.update(function).root().get()).getEntity(id()).orElse(null);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Entity<T> apply(Message eventMessage) {
        return (Entity<T>) root.apply(eventMessage).getEntity(id()).orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Entity<T> commit() {
        return (Entity<T>) root.commit().getEntity(id()).orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Entity<T> assertAndApply(Object payloadOrMessage) {
        return (Entity<T>) root.assertAndApply(payloadOrMessage).getEntity(id()).orElse(null);
    }

    @Override
    public Collection<? extends Entity<?>> entities() {
        return super.entities().stream().map(e -> new ModifiableEntity<>(e, root)).collect(Collectors.toList());
    }

    @Override
    public Entity<?> parent() {
        return ofNullable(super.parent()).map(entity -> new ModifiableEntity<>(entity, root)).orElse(null);
    }

    @Override
    public Entity<T> previous() {
        return ofNullable(super.previous()).map(e -> new ModifiableEntity<>(e, root)).orElse(null);
    }
}
