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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;

import java.beans.Transient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

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
        return Optional.ofNullable(message.getMetadata().get(AGGREGATE_TYPE_METADATA_KEY))
                .map(c -> ReflectionUtils.classForName(c, null)).orElse(null);
    }

    static Long getSequenceNumber(HasMetadata message) {
        return Optional.ofNullable(message.getMetadata().get(AGGREGATE_SN_METADATA_KEY))
                .map(Long::parseLong).orElse(null);
    }

    Object id();

    Class<T> type();

    Entity<T> withType(Class<T> type);

    T get();

    @Transient
    default boolean isEmpty() {
        return get() == null;
    }

    @Transient
    default boolean isPresent() {
        return get() != null;
    }

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

    default List<Entity<?>> ancestors() {
        List<Entity<?>> result = new ArrayList<>();
        for (Entity<?> p = parent(); p != null; p = p.parent()) {
            result.add(p);
        }
        return result.reversed();
    }

    default <A> A ancestorValue(Class<A> ancestorType) {
        for (Entity<?> ancestor = this; ancestor != null; ancestor = ancestor.parent()) {
            if (ancestorType.isAssignableFrom(ancestor.type())) {
                return ancestorType.cast(ancestor.get());
            }
        }
        return null;
    }

    Collection<?> aliases();

    @JsonIgnore
    default boolean isRoot() {
        return parent() == null;
    }

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

    default Long highestEventIndex() {
        return ofNullable(lastEventIndex()).or(
                () -> ofNullable(previous()).map(Entity::highestEventIndex)).orElse(null);
    }

    default Entity<T> withEventIndex(Long index, String messageId) {
        return root().withEventIndex(index, messageId).findEntity(id(), type());
    }

    default Entity<T> withSequenceNumber(long sequenceNumber) {
        return root().withSequenceNumber(sequenceNumber).findEntity(id(), type());
    }

    default Instant timestamp() {
        return root().timestamp();
    }

    default long sequenceNumber() {
        return root().sequenceNumber();
    }

    default Aggregate rootAnnotation() {
        return DefaultEntityHelper.getRootAnnotation(root().type());
    }

    default Entity<T> previous() {
        return root().previous().findEntity(id(), type());
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

    @SuppressWarnings("unchecked")
    default <C> Optional<Entity<C>> getEntity(Object entityId) {
        return entityId == null ? Optional.empty() : allEntities().filter(
                e -> entityId.equals(e.id()) || e.aliases().contains(entityId)).findFirst().map(e -> (Entity<C>) e);
    }

    default Set<Relationship> relationships() {
        if (get() == null) {
            return Collections.emptySet();
        }
        String id = id().toString();
        String type = type().getName();
        return allEntities().filter(e -> e.id() != null)
                .flatMap(e -> Stream.concat(Stream.of(e.id()), e.aliases().stream()))
                .map(entityId -> Relationship.builder()
                        .entityId(entityId.toString()).aggregateType(type).aggregateId(id).build())
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
        return event instanceof DeserializingMessage d ? apply(d) : apply(asMessage(event));
    }

    default Entity<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    default Entity<T> apply(DeserializingMessage eventMessage) {
        return apply(eventMessage.toMessage());
    }

    Entity<T> apply(Message eventMessage);

    Entity<T> commit();

    <E extends Exception> Entity<T> assertLegal(Object command) throws E;

    default Entity<T> assertAndApply(Object payloadOrMessage) {
        return assertLegal(payloadOrMessage).apply(payloadOrMessage);
    }

    default Entity<T> assertAndApply(Object payload, Metadata metadata) {
        return assertAndApply(new Message(payload, metadata));
    }

    default Entity<T> assertAndApply(Object... events) {
        return assertAndApply(List.of(events));
    }

    default Entity<T> assertAndApply(Collection<?> events) {
        Entity<T> result = this;
        for (Object event : events) {
            result = result.assertAndApply(event);
        }
        return result;
    }

    default Iterable<Entity<?>> possibleTargets(Object payload) {
        if (payload == null) {
            return emptyList();
        }
        for (Entity<?> e : entities()) {
            if (e.isPossibleTarget(payload, false)) {
                return singletonList(e);
            }
        }
        List<Entity<?>> result = new ArrayList<>();
        for (Entity<?> e : entities()) {
            if (e.isPossibleTarget(payload, true)) {
                result.add(e);
            }
        }
        return switch (result.size()) {
            case 0 -> emptyList();
            case 1 -> result.subList(0, 1);
            default -> {
                result.sort(Comparator.<Entity<?>>comparingInt(Entity::depth).reversed());
                yield result.subList(0, 1);
            }
        };
    }

    default int depth() {
        Entity<?> parent = parent();
        return parent == null ? 0 : 1 + parent.depth();
    }

    private boolean isPossibleTarget(Object message, boolean includeEmpty) {
        if (message == null) {
            return false;
        }
        String idProperty = idProperty();
        if (idProperty == null) {
            return includeEmpty;
        }
        Object payload = message instanceof Message ? ((Message) message).getPayload() : message;
        Object id = id();
        if (id == null) {
            return includeEmpty && isEmpty() && hasProperty(idProperty, payload);
        }
        if (readProperty(idProperty, payload)
                .or(() -> getAnnotatedPropertyValue(payload, RoutingKey.class)).map(id::equals).orElse(false)) {
            return true;
        }
        if (isPresent() && !hasProperty(idProperty, payload)) {
            for (Entity<?> e : entities()) {
                if (e.isPresent() && e.isPossibleTarget(message, includeEmpty)) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private <U> Entity<U> findEntity(Object id, Class<U> type) {
        return (Entity<U>) allEntities().filter(e -> Objects.equals(e.id(), id) && e.type().isAssignableFrom(type))
                .findFirst().orElse(null);
    }

    @FunctionalInterface
    interface Validator<T, E extends Exception> {
        void validate(T model) throws E;
    }
}