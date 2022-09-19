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

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.publishing.routing.RoutingKey;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedPropertyValue;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.hasProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.readProperty;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static java.util.Collections.emptyList;

public interface Entity<T> {

    Object id();

    Class<T> type();

    T get();

    String idProperty();

    Entity<?> parent();

    @SuppressWarnings("rawtypes")
    default AggregateRoot<?> root() {
        return Optional.<Entity>ofNullable(parent()).map(Entity::root).orElseGet(() -> (AggregateRoot<?>) this);
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

    Collection<? extends Entity<?>> entities();

    default Stream<Entity<?>> allEntities() {
        return Stream.concat(Stream.of(this), entities().stream().flatMap(Entity::allEntities));
    }

    default Optional<Entity<?>> getEntity(Object entityId) {
        return allEntities().filter(e -> entityId != null && entityId.equals(e.id())).findFirst();
    }

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

    default <E extends Exception> Entity<T> assertLegal(Object command) throws E {
        ValidationUtils.assertLegal(command, this);
        return this;
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

    default Entity<T> assertAndApply(Object payload) {
        return assertLegal(payload).apply(payload);
    }

    default Entity<T> assertAndApply(Object payload, Metadata metadata) {
        return assertLegal(payload).apply(payload, metadata);
    }

    default Iterable<Entity<?>> possibleTargets(Object payload) {
        for (Entity<?> e : entities()) {
            if (e.isPossibleTarget(payload)) {
                return List.of(e);
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