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
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.fluxcapacitor.common.handling.HandlerInvoker;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Apply;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.AccessibleObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperties;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotationAs;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getValue;
import static io.fluxcapacitor.javaclient.modeling.AnnotatedEntityHolder.getEntityHolder;
import static java.util.Collections.emptyList;


/**
 * Immutable implementation of the {@link Entity} interface, representing a snapshot of a domain entity.
 * <p>
 * Unlike mutable entities, an {@code ImmutableEntity} is never changed in-place. Instead, updates such as event
 * applications or transformations return a new, updated instance, preserving immutability and making it suitable
 * for event-sourced state transitions, testing, and functional-style programming models.
 * <p>
 * This entity is typically wrapped by higher-level mutable structures such as {@code ModifiableAggregateRoot},
 * which manage lifecycle and mutation tracking.
 *
 * <h2>Key Features</h2>
 * <ul>
 *     <li>Supports event application via methods annotated with {@code @Apply}</li>
 *     <li>Supports recursive validation using {@code @AssertLegal}</li>
 *     <li>Automatically resolves child entities and aliases using {@code @Member} and {@code @Alias} annotations</li>
 *     <li>Preserves immutability by returning a new instance after each update</li>
 * </ul>
 *
 * <h2>Common Usage</h2>
 * <pre>{@code
 * ImmutableEntity<Order> orderEntity = ImmutableEntity.<Order>builder()
 *     .id("order123")
 *     .type(Order.class)
 *     .value(order)
 *     .entityHelper(entityHelper)
 *     .serializer(serializer)
 *     .build();
 *
 * // Apply an event and receive a new version of the entity
 * orderEntity = orderEntity.apply(eventMessage);
 * }</pre>
 *
 * <p>
 * The {@link #update(UnaryOperator)} method applies a transformation to the entity value and automatically
 * updates parent references when nested.
 * <p>
 * Event application is handled via the {@link EntityHelper} which dynamically locates appropriate handlers.
 * If no direct handler is found, the event is propagated recursively to nested child entities.
 *
 * @param <T> the type of the underlying domain object represented by this entity
 * @see Entity
 * @see Apply
 * @see Alias
 * @see Member
 */
@Value
@NonFinal
@SuperBuilder(toBuilder = true)
@Accessors(fluent = true)
@Slf4j
public class ImmutableEntity<T> implements Entity<T> {
    @JsonProperty
    Object id;
    @JsonProperty
    Class<T> type;
    @ToString.Exclude
    @JsonProperty
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type")
    @Getter(AccessLevel.PROTECTED)
    T value;
    @JsonProperty
    String idProperty;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Entity<?> parent;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient AnnotatedEntityHolder holder;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient EntityHelper entityHelper;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    transient Serializer serializer;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<? extends Entity<?>> entities = computeEntities();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Getter(lazy = true)
    Collection<?> aliases = computeAliases();

    @SuppressWarnings("unchecked")
    public Class<T> type() {
        T value = get();
        return value == null ? type : (Class<T>) value.getClass();
    }

    @Override
    public Entity<T> withType(Class<T> type) {
        if (!type().isAssignableFrom(type)) {
            throw new IllegalArgumentException("Given type is not assignable to entity type");
        }
        return toBuilder().type(type).build();
    }

    @Override
    public T get() {
        return value;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Entity<T> update(UnaryOperator<T> function) {
        ImmutableEntity<T> after = toBuilder().value(function.apply(get())).build();
        return parent == null ? after : (Entity<T>) parent.update(
                (UnaryOperator) p -> holder.updateOwner(p, this, after)).getEntity(id()).orElse(null);
    }

    @Override
    public Entity<T> apply(Message message) {
        return apply(new DeserializingMessage(message, EVENT, null, serializer));
    }

    @Override
    public Entity<T> commit() {
        return this;
    }

    @Override
    public <E extends Exception> Entity<T> assertLegal(Object update) throws E {
        entityHelper.assertLegal(update, root());
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Entity<T> apply(DeserializingMessage message) {
        Optional<HandlerInvoker> invoker = entityHelper.applyInvoker(message, this);
        if (invoker.isPresent()) {
            return toBuilder().value((T) invoker.get().invoke()).build();
        }
        ImmutableEntity<T> result = this;
        Object payload = message.getPayload();
        for (Entity<?> entity : result.possibleTargets(payload)) {
            ImmutableEntity<?> immutableEntity = (ImmutableEntity<?>) entity;
            Entity<?> updated = immutableEntity.apply(message);
            if (immutableEntity.get() != updated.get()) {
                result = result.toBuilder().value((T) immutableEntity
                        .holder().updateOwner(result.get(), entity, updated)).build();
            }
        }
        return result;
    }

    protected Collection<? extends ImmutableEntity<?>> computeEntities() {
        Class<?> type = type();
        List<ImmutableEntity<?>> result = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(type, Member.class)) {
            result.addAll(getEntityHolder(type, location, entityHelper, serializer)
                                  .getEntities(this).toList());
        }
        return result;
    }

    protected Collection<?> computeAliases() {
        Object target = get();
        if (target == null) {
            return emptyList();
        }
        List<Object> results = new ArrayList<>();
        for (AccessibleObject location : getAnnotatedProperties(target.getClass(), Alias.class)) {
            Object v = getValue(location, target, false);
            if (v != null) {
                getAnnotationAs(location, Alias.class, Alias.class).ifPresent(alias -> {
                    UnaryOperator<Object> aliasFunction = id -> "".equals(alias.prefix()) && "".equals(alias.postfix())
                            ? id : alias.prefix() + id + alias.postfix();
                    if (v instanceof Collection<?> collection) {
                        results.addAll(collection.stream().filter(Objects::nonNull).map(aliasFunction).toList());
                    } else {
                        results.add(aliasFunction.apply(v));
                    }
                });
            }
        }
        return results;
    }
}
