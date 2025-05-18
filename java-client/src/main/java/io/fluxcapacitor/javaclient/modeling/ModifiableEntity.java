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
