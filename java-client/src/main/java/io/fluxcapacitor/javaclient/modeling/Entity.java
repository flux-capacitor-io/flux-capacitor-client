/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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

import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.hasProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.lang.String.format;
import static java.util.Collections.emptyList;

public interface Entity<T> {

    ThreadLocal<Boolean> loading = ThreadLocal.withInitial(() -> false);
    ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);
    String AGGREGATE_ID_METADATA_KEY = "$aggregateId";
    String AGGREGATE_TYPE_METADATA_KEY = "$aggregateType";
    String AGGREGATE_SN_METADATA_KEY = "$sequenceNumber";

    static boolean isLoading() {
        return loading.get();
    }

    static boolean isApplying() {
        return applying.get();
    }

    static String getAggregateId(HasMetadata message) {
        return message.getMetadata().get(AGGREGATE_ID_METADATA_KEY);
    }

    static Class<?> getAggregateType(HasMetadata message) {
        return Optional.ofNullable(message.getMetadata().get(AGGREGATE_TYPE_METADATA_KEY)).map(c -> {
            try {
                return ReflectionUtils.classForName(c);
            } catch (Exception ignored) {
                return null;
            }
        }).orElse(null);
    }

    static Long getSequenceNumber(HasMetadata message) {
        return Optional.ofNullable(message.getMetadata().get(AGGREGATE_SN_METADATA_KEY))
                .map(Long::parseLong).orElse(null);
    }

    Object id();

    Class<T> type();

    T get();

    default Entity<T> ifPresent(UnaryOperator<Entity<T>> action) {
        if (get() == null) {
            return this;
        }
        return action.apply(this);
    }

    default <U> Optional<U> mapIfPresent(Function<Entity<T>, U> action) {
        if (get() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(action.apply(this));
    }

    String idProperty();

    Entity<?> parent();

    @SuppressWarnings("rawtypes")
    default Entity<?> root() {
        return Optional.<Entity>ofNullable(parent()).map(Entity::root).orElse(this);
    }

    default String lastEventId() {
        return root().lastEventId();
    }

    default Long lastEventIndex() {
        return root().lastEventIndex();
    }

    default Instant timestamp() {
        return root().timestamp();
    }

    default long sequenceNumber() {
        return root().sequenceNumber();
    }

    @SuppressWarnings("unchecked")
    default Entity<T> previous() {
        return (Entity<T>) root().previous().allEntities().filter(
                e -> Objects.equals(e.id(), id()) && Objects.equals(e.type(), type())).findFirst().orElse(null);
    }

    default Entity<T> playBackToEvent(String eventId) {
        return playBackToCondition(aggregate -> Objects.equals(eventId, aggregate.lastEventId()))
                .orElseThrow(() -> new IllegalStateException(format(
                        "Could not load aggregate %s of type %s for event %s. Aggregate (%s) started at event %s",
                        id(), type().getSimpleName(), eventId, this, lastEventId())));
    }

    default Optional<Entity<T>> playBackToCondition(Predicate<Entity<T>> condition) {
        Entity<T> result = this;
        while (result != null && !condition.test(result)) {
            result = result.previous();
        }
        return Optional.ofNullable(result);
    }

    Collection<? extends Entity<?>> entities();

    default Stream<Entity<?>> allEntities() {
        return Stream.concat(Stream.of(this), entities().stream().flatMap(Entity::allEntities));
    }

    default Optional<Entity<?>> getEntity(Object entityId) {
        return allEntities().filter(e -> entityId != null && entityId.equals(e.id())).findFirst();
    }

    default Entity<T> makeReadOnly() {
        if (this instanceof ReadOnlyEntity<?>) {
            return this;
        }
        return new ReadOnlyEntity<>(this);
    }

    default Set<Relationship> relationships() {
        String id = id().toString();
        String type = type().getName();
        return get() == null ? Collections.emptySet()
                : allEntities().map(Entity::id).filter(Objects::nonNull)
                .map(entityId -> Relationship.builder().entityId(entityId.toString()).aggregateType(type)
                        .aggregateId(id).build())
                .collect(Collectors.toSet());
    }

    default Set<Relationship> associations(Entity<?> previous) {
        Set<Relationship> result = new HashSet<>(relationships());
        result.removeAll(previous.relationships());
        return result;
    }

    default Set<Relationship> dissociations(Entity<?> previous) {
        Set<Relationship> result = new HashSet<>(previous.relationships());
        result.removeAll(relationships());
        return result;
    }

    Entity<T> update(UnaryOperator<T> function);

    default Entity<T> apply(Object... events) {
        return apply(List.of(events));
    }

    default Entity<T> apply(Collection<?> events) {
        Entity<T> result = this;
        for (Object event : events) {
            result = result.apply(event);
        }
        return result;
    }

    default Entity<T> apply(Object event) {
        if (event instanceof DeserializingMessage) {
            return apply(((DeserializingMessage) event).toMessage());
        }
        return apply(asMessage(event));
    }

    default Entity<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    Entity<T> apply(Message eventMessage);

    <E extends Exception> Entity<T> assertLegal(Object command) throws E;

    default Entity<T> assertAndApply(Object payloadOrMessage) {
        return assertLegal(payloadOrMessage).apply(payloadOrMessage);
    }

    default Entity<T> assertAndApply(Object payload, Metadata metadata) {
        return assertAndApply(new Message(payload, metadata));
    }

    default <E extends Exception> Entity<T> assertThat(Validator<T, E> validator) throws E {
        validator.validate(this.get());
        return this;
    }

    default <E extends Exception> Entity<T> ensure(Predicate<T> check, Function<T, E> errorProvider) throws E {
        if (!check.test(get())) {
            throw errorProvider.apply(get());
        }
        return this;
    }

    default Iterable<Entity<?>> possibleTargets(Object payload) {
        if (payload != null) {
            for (Entity<?> e : entities()) {
                if (e.isPossibleTarget(payload)) {
                    return List.of(e);
                }
            }
        }
        return emptyList();
    }

    private boolean isPossibleTarget(Object message) {
        if (message == null) {
            return false;
        }
        for (Entity<?> e : entities()) {
            if (e.isPossibleTarget(message)) {
                return true;
            }
        }
        String idProperty = idProperty();
        Object id = id();
        if (idProperty == null) {
            return true;
        }
        if (id == null && get() != null) {
            return false;
        }
        Object payload = message instanceof Message ? ((Message) message).getPayload() : message;
        if (id == null) {
            return hasProperty(idProperty, payload);
        }
        return readProperty(idProperty, payload)
                .or(() -> getAnnotatedPropertyValue(payload, RoutingKey.class)).map(id::equals).orElse(false);
    }

    @FunctionalInterface
    interface Validator<T, E extends Exception> {
        void validate(T model) throws E;
    }
}