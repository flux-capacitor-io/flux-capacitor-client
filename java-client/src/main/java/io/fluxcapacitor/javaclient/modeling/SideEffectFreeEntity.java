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
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.toList;

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
    public <E extends Exception> Entity<T> assertLegal(Object command) throws E {
        delegate().assertLegal(command);
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
