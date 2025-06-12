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

import io.fluxcapacitor.common.api.HasMetadata;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.HasMessage;
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

/**
 * Represents an entity that encapsulates domain behavior and state. An entity is a core construct of domain modeling
 * and may have a unique identity, a set of attributes, and relationships with other entities. This interface defines
 * the contract for managing an entity's type, identity, lifecycle, relationships, and operations that allow state
 * mutations with event sourcing.
 *
 * @param <T> the type of the entity's state, typically an immutable value object like UserProfile
 */
public interface Entity<T> {

    ThreadLocal<Boolean> loading = ThreadLocal.withInitial(() -> false);
    ThreadLocal<Boolean> applying = ThreadLocal.withInitial(() -> false);

    /**
     * A constant key used for identifying the aggregate ID in metadata of events. The value associated with this key
     * represents the unique identifier of an aggregate to which the event was applied.
     */
    String AGGREGATE_ID_METADATA_KEY = "$aggregateId";

    /**
     * A constant key used for identifying the aggregate type in metadata of events. The value associated with this key
     * represents the type of aggregate to which the event was applied.
     */
    String AGGREGATE_TYPE_METADATA_KEY = "$aggregateType";

    /**
     * A constant key used for identifying the sequence number of an applied event in metadata.
     */
    String AGGREGATE_SN_METADATA_KEY = "$sequenceNumber";

    /**
     * Indicates whether any entity is currently being loaded in this thread.
     *
     * @return true if any entity is currently being loaded, false otherwise.
     */
    static boolean isLoading() {
        return loading.get();
    }

    /**
     * Indicates whether any entity is currently being applied to in this thread.
     *
     * @return true if any entity is currently being applied to, false otherwise.
     */
    static boolean isApplying() {
        return applying.get();
    }

    /**
     * Retrieves the aggregate ID from the metadata of the given message.
     *
     * @param message the message containing metadata from which the aggregate ID is retrieved
     * @return the aggregate ID extracted from the metadata, or null if the key is not present
     */
    static String getAggregateId(HasMetadata message) {
        return message.getMetadata().get(AGGREGATE_ID_METADATA_KEY);
    }

    /**
     * Retrieves the aggregate type from the metadata of the given message.
     *
     * @param message the message containing metadata from which the aggregate type is retrieved
     * @return the aggregate type as a {@code Class<?>}, or {@code null} if the metadata key is not present
     */
    static Class<?> getAggregateType(HasMetadata message) {
        return Optional.ofNullable(message.getMetadata().get(AGGREGATE_TYPE_METADATA_KEY))
                .map(c -> ReflectionUtils.classForName(c, null)).orElse(null);
    }

    /**
     * Determines whether the given message contains a sequence number in its metadata.
     *
     * @param message the message to check for the presence of a sequence number in its metadata
     * @return true if the metadata contains the key for the sequence number, false otherwise
     */
    static boolean hasSequenceNumber(HasMetadata message) {
        return message.getMetadata().containsKey(AGGREGATE_SN_METADATA_KEY);
    }

    /**
     * Retrieves the sequence number from the metadata of the given message. The sequence number is extracted as a
     * {@code Long} if present; otherwise, {@code null} is returned.
     *
     * @param message the message containing metadata from which the sequence number is retrieved
     * @return the sequence number as a {@code Long}, or {@code null} if the metadata key is not present or the value
     * cannot be parsed
     */
    static Long getSequenceNumber(HasMetadata message) {
        return Optional.ofNullable(message.getMetadata().get(AGGREGATE_SN_METADATA_KEY))
                .map(Long::parseLong).orElse(null);
    }

    /**
     * Retrieves the unique identifier of the entity.
     *
     * @return the unique identifier of the entity, or null if this entity has not been initialized yet
     */
    Object id();

    /**
     * Retrieves the type of the entity.
     *
     * @return the class type of the entity, or null if the type has not been defined
     */
    Class<T> type();

    /**
     * Sets the type of the entity to the specified class and returns the updated entity.
     *
     * @param type the class representing the type to be set for the entity
     * @return the updated entity with the specified type
     */
    Entity<T> withType(Class<T> type);

    /**
     * Retrieves the current instance of the entity.
     *
     * @return the current instance of the entity or null if not initialized
     */
    T get();

    /**
     * Checks if the current entity instance is empty.
     * <p>
     * This method determines whether the value held by this entity is `null`.
     *
     * @return true if the entity's current instance is null, false otherwise
     */
    @Transient
    default boolean isEmpty() {
        return get() == null;
    }

    /**
     * Checks whether the entity is considered present.
     *
     * @return true if the current instance of the entity is not null, false otherwise
     */
    @Transient
    default boolean isPresent() {
        return get() != null;
    }

    /**
     * Applies the specified action to this entity if it is present, returning the result of the action. If this entity
     * is not present (i.e., its value is null), the entity itself is returned unchanged.
     *
     * @param action the action to apply to this entity if it is present; must be a {@link UnaryOperator} that accepts
     *               and returns an {@code Entity<T>}
     * @return the result of applying the action to this entity if it is present, or this entity itself if it is not
     * present
     */
    default Entity<T> ifPresent(UnaryOperator<Entity<T>> action) {
        if (get() == null) {
            return this;
        }
        return action.apply(this);
    }

    /**
     * Maps the entity to a new value if the entity is present. This method applies the provided mapping function to the
     * entity if it is initialized and returns an {@link Optional} containing the result or an empty {@link Optional} if
     * the entity is not present.
     *
     * @param <U>    the type of the result of applying the mapping function
     * @param action the mapping function to apply to the entity if present
     * @return an {@link Optional} containing the result of the mapping function, or an empty {@link Optional} if the
     * entity is not present
     */
    default <U> Optional<U> mapIfPresent(Function<Entity<T>, U> action) {
        if (get() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(action.apply(this));
    }

    /**
     * Retrieves the name of the property that serves as the unique identifier for the entity.
     *
     * @return the name of the identification property as a string
     */
    String idProperty();

    /**
     * Retrieves the parent entity of the current entity.
     *
     * @return the parent entity of the current entity, or null if this entity does not have a parent
     */
    Entity<?> parent();

    /**
     * Retrieves a list of the ancestor entities for the current entity. The ancestors are ordered from the farthest
     * ancestor to the closest parent.
     *
     * @return a list of ancestor entities in hierarchical order, with the root entity first and the closest parent last
     */
    default List<Entity<?>> ancestors() {
        List<Entity<?>> result = new ArrayList<>();
        for (Entity<?> p = parent(); p != null; p = p.parent()) {
            result.add(p);
        }
        return result.reversed();
    }

    /**
     * Retrieves the value of the first ancestor entity in the hierarchy that matches the specified type. The search
     * iterates through the ancestors of the current entity until one of the specified type is found. If no matching
     * ancestor is found, this method returns null.
     *
     * @param <A>          the type of the ancestor entity to search for
     * @param ancestorType the class representing the type of the ancestor entity to find
     * @return the value of the matching ancestor entity cast to the specified type, or null if no such ancestor is
     * found
     */
    default <A> A ancestorValue(Class<A> ancestorType) {
        for (Entity<?> ancestor = this; ancestor != null; ancestor = ancestor.parent()) {
            if (ancestorType.isAssignableFrom(ancestor.type())) {
                return ancestorType.cast(ancestor.get());
            }
        }
        return null;
    }

    /**
     * Retrieves a collection of aliases associated with this entity.
     *
     * @return a collection containing aliases of this entity, or an empty collection if no aliases are present
     */
    Collection<?> aliases();

    /**
     * Determines whether the current entity is the root entity in its hierarchical structure.
     * <p>
     * This method checks if the entity has no parent, indicating it is at the top level of the hierarchy.
     *
     * @return true if the current entity has no parent and is considered the root entity, false otherwise
     */
    @Transient
    default boolean isRoot() {
        return parent() == null;
    }

    /**
     * Retrieves the root entity in the hierarchy. The root entity is determined by traversing up the parent hierarchy
     * until a root entity, which has no parent, is found.
     *
     * @return the root entity if a parent hierarchy exists, or this entity itself if no parent is present
     */
    @SuppressWarnings("rawtypes")
    default Entity<?> root() {
        return Optional.<Entity>ofNullable(parent()).map(Entity::root).orElse(this);
    }

    /**
     * Retrieves the identifier of the last event applied to the entity hierarchy.
     *
     * @return the identifier of the last event as a string, or null if no events have been recorded in the entity's
     * aggregate
     */
    default String lastEventId() {
        return root().lastEventId();
    }

    /**
     * Retrieves the index of the last event applied to this entity hierarchy.
     *
     * @return the index of the last event as a {@code Long}, or null if no events have been recorded for the entity's
     * aggregate
     */
    default Long lastEventIndex() {
        return root().lastEventIndex();
    }

    /**
     * Retrieves the highest event index associated with the aggregate. The method resolves the
     * {@link #lastEventIndex()}. If that's null, it recursively checks the last event index of previous versions of the
     * entity. Returns null if no version of this entity has an event index.
     *
     * @return the highest event index as a {@code Long}, or null if no event index is found
     */
    default Long highestEventIndex() {
        return ofNullable(lastEventIndex()).or(
                () -> ofNullable(previous()).map(Entity::highestEventIndex)).orElse(null);
    }

    /**
     * Updates the event index and message ID of the entity and returns the updated version of the entity.
     *
     * @param index     the event index to set for the root entity
     * @param messageId the message ID associated with the event
     * @return the updated entity corresponding to the current entity's ID and type, or null if no such entity can be
     * found
     */
    default Entity<T> withEventIndex(Long index, String messageId) {
        return root().withEventIndex(index, messageId).findEntity(id(), type());
    }

    /**
     * Returns an updated entity based on this with the specified sequence number.
     *
     * @param sequenceNumber the sequence number to assign to the entity
     * @return the updated entity with the specified sequence number, or null if the entity could not be found in the
     * entity hierarchy
     */
    default Entity<T> withSequenceNumber(long sequenceNumber) {
        return root().withSequenceNumber(sequenceNumber).findEntity(id(), type());
    }

    /**
     * Retrieves the timestamp of the entity.
     *
     * @return the timestamp as an {@link Instant} obtained from the root entity
     */
    default Instant timestamp() {
        return root().timestamp();
    }

    /**
     * Retrieves the sequence number of the current entity.
     *
     * @return the sequence number of the root entity
     */
    default long sequenceNumber() {
        return root().sequenceNumber();
    }

    /**
     * Retrieves the root-level annotation of the aggregate, if any.
     *
     * @return the {@link Aggregate} annotation of the root entity, or null if not found
     */
    default Aggregate rootAnnotation() {
        return DefaultEntityHelper.getRootAnnotation(root().type());
    }

    /**
     * Retrieves the previous version of this entity.
     *
     * @return the previous state of the entity, or null if this is the first known version
     */
    default Entity<T> previous() {
        return root().previous().findEntity(id(), type());
    }

    /**
     * Finds a version of this entity matching the given eventIndex or eventId. This method plays back the aggregate
     * until the aggregate's {@link #lastEventIndex()} smaller or equal to eventIndex or {@link #lastEventId()} equals
     * eventId.
     *
     * @param eventIndex the index of the event to revert to, or null if the index is not provided
     * @param eventId    the unique ID of the event to revert to
     * @return the aggregate entity reverted to the specified event state
     * @throws IllegalStateException if the playback to the event state fails
     */
    default Entity<T> playBackToEvent(Long eventIndex, String eventId) {
        return playBackToCondition(aggregate -> {
            if (Objects.equals(eventId, aggregate.lastEventId())) {
                return true;
            }
            if (eventIndex == null) {
                return false;
            }
            Long aggregateIndex = aggregate.lastEventIndex();
            return aggregateIndex != null && aggregateIndex <= eventIndex;
        }).orElseThrow(() -> new IllegalStateException(format(
                "Could not load aggregate %s of type %s for event %s. Aggregate (%s) started at event %s",
                id(), type().getSimpleName(), eventIndex, this, lastEventIndex())));
    }

    /**
     * Plays back through older versions of this entity until a specified condition is met. This method starts from the
     * current entity and iteratively moves to previous versions of the entity until the condition is satisfied or there
     * are no older versions.
     *
     * @param condition the predicate condition to evaluate against each entity while playing back
     * @return an Optional containing the first entity that meets the condition, or an empty Optional if no such entity
     * is found
     */
    default Optional<Entity<T>> playBackToCondition(Predicate<Entity<T>> condition) {
        Entity<T> result = this;
        while (result != null && !condition.test(result)) {
            result = result.previous();
        }
        return Optional.ofNullable(result);
    }

    /**
     * Retrieves child entities of this entity.
     *
     * @return a collection containing child entities.
     */
    Collection<? extends Entity<?>> entities();

    /**
     * Retrieves a stream of all entities, including the current entity and all nested entities.
     *
     * @return a {@link Stream} containing the current entity and all nested entities within it.
     */
    default Stream<Entity<?>> allEntities() {
        return Stream.concat(Stream.of(this), entities().stream().flatMap(Entity::allEntities));
    }

    /**
     * Retrieves an entity based on the provided entity ID. The entity can be matched either by its primary ID or by any
     * of its aliases.
     *
     * @param entityId the ID or alias of the entity to search for; if null, an empty {@code Optional} is returned
     * @param <C>      the type parameter representing the content or payload of the entity
     * @return an {@code Optional} containing the matching entity if found, or an empty {@code Optional} if no match
     * exists
     */
    @SuppressWarnings("unchecked")
    default <C> Optional<Entity<C>> getEntity(Object entityId) {
        return entityId == null ? Optional.empty() : allEntities().filter(
                e -> entityId.equals(e.id()) || e.aliases().contains(entityId)).findFirst().map(e -> (Entity<C>) e);
    }

    /**
     * Retrieves the set of relationships between the aggregate root and all its entities.
     * <p>
     * If the current entity is not a root entity, this method delegates to the root entity's relationships. If the root
     * entity is empty, an empty set is returned. Otherwise, the relationships are computed based on the entity's ID,
     * type, and aliases.
     *
     * @return a set of {@code Relationship} objects representing the relationships associated with the aggregate root.
     */
    default Set<Relationship> relationships() {
        if (!isRoot()) {
            return root().relationships();
        }
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

    /**
     * Determines the set of new associations (relationships) that are present in the current entity but not in the
     * provided previous entity.
     *
     * @param previous The previous entity whose relationships will be excluded from the current entity's
     *                 relationships.
     * @return A set of relationships that exist in the current entity but not in the previous entity.
     */
    default Set<Relationship> associations(Entity<?> previous) {
        Set<Relationship> result = new HashSet<>(relationships());
        result.removeAll(previous.relationships());
        return result;
    }

    /**
     * Identifies and returns the set of relationships that are dissociated when compared to the relationships of the
     * given previous entity.
     *
     * @param previous the entity whose relationships are to be compared against the current entity's relationships.
     * @return a set of relationships that exist in the given entity but are not present in the current entity.
     */
    default Set<Relationship> dissociations(Entity<?> previous) {
        Set<Relationship> result = new HashSet<>(previous.relationships());
        result.removeAll(relationships());
        return result;
    }

    /**
     * Updates the current entity's value using the specified unary operator and returns a new entity containing the
     * updated value.
     *
     * @param function the unary operator to apply to the current entity's value
     * @return a new entity containing the updated value after applying the function
     */
    Entity<T> update(UnaryOperator<T> function);

    /**
     * Applies the given events to reconstruct or update the entity's state.
     *
     * @param events an array of events to be applied, represented as objects
     * @return the resulting entity after applying the events
     */
    default Entity<T> apply(Object... events) {
        return apply(List.of(events));
    }

    /**
     * Applies a collection of events to the current entity, returning an updated entity.
     *
     * @param events a collection of events to be applied to the entity
     * @return the updated entity after all events have been applied
     */
    default Entity<T> apply(Collection<?> events) {
        Entity<T> result = this;
        for (Object event : events) {
            result = result.apply(event);
        }
        return result;
    }

    /**
     * Processes the given event and applies the corresponding logic based on its type.
     *
     * @param event the event object to be processed. It can be the payload of the event or an event message.
     * @return the result of the operation as an {@code Entity<T>} instance.
     */
    default Entity<T> apply(Object event) {
        return event instanceof DeserializingMessage d ? apply(d) : apply(asMessage(event));
    }

    /**
     * Applies an event and associated metadata to produce a resulting entity.
     *
     * @param event    the event object that will be applied
     * @param metadata metadata associated with the event
     * @return the resulting entity after applying the event and metadata
     */
    default Entity<T> apply(Object event, Metadata metadata) {
        return apply(new Message(event, metadata));
    }

    /**
     * Applies the given deserializing message to the entity.
     *
     * @param eventMessage the deserializing message to process and convert into an entity
     * @return the entity resulting from applying the given deserializing message
     */
    default Entity<T> apply(DeserializingMessage eventMessage) {
        return apply(eventMessage.toMessage());
    }

    /**
     * Applies the given message to the entity.
     *
     * @param eventMessage the message representing the event to be applied
     * @return the updated or newly created Entity of type T
     */
    Entity<T> apply(Message eventMessage);

    /**
     * Commits the current state of the entity, persisting any changes made to it. This method ensures that the
     * modifications are saved. Typically, it is unnecessary to invoke this manually as it is automatically invoked
     * after the current handler or consumer batch has completed.
     *
     * @return The updated entity after the commit operation is successfully completed.
     */
    Entity<T> commit();

    /**
     * Verifies that the provided update is legal given the current state of the aggregate. If so, the entity is
     * returned; otherwise, it throws an appropriate exception.
     *
     * @param update the update to be validated for compliance with the required rules
     * @param <E>    the type of exception expected if the update is not legal
     * @return the entity if the update is legal
     * @throws E if the update fails to meet legal requirements
     */
    <E extends Exception> Entity<T> assertLegal(Object update) throws E;

    /**
     * Verifies that the provided update is legal given the current state of the aggregate and on success applies it to
     * the aggregate. If not, it throws an appropriate exception.
     *
     * @param payloadOrMessage the input object to be applied; can be a payload or a message
     * @return the resulting entity after application
     */
    default Entity<T> assertAndApply(Object payloadOrMessage) {
        return assertLegal(payloadOrMessage).apply(payloadOrMessage);
    }

    /**
     * Verifies that the provided update is legal given the current state of the aggregate and on success applies it to
     * the aggregate. If the update is not legal, it throws an appropriate exception.
     *
     * @param payload  The object representing the payload to be validated and processed.
     * @param metadata The metadata associated with the payload for validation and processing.
     * @return An Entity instance containing the result after processing the payload and metadata.
     */
    default Entity<T> assertAndApply(Object payload, Metadata metadata) {
        return assertAndApply(new Message(payload, metadata));
    }

    /**
     * Applies a number of updates to the current entity after performing necessary legality assertions. If any of the
     * updates is not legal, it rolls back the entity and throws an appropriate exception.
     *
     * @param updates an array of updates to be processed and applied to the entity
     * @return an updated Entity instance after applying the provided updates
     */
    default Entity<T> assertAndApply(Object... updates) {
        return assertAndApply(List.of(updates));
    }

    /**
     * Applies a collection of updates sequentially to the current entity after asserting each is legal, and returns the
     * resulting entity. If any of the updates is not legal, it rolls back the entity and throws an appropriate
     * exception.
     *
     * @param updates the collection of updates to be applied to the entity
     * @return the resulting entity after applying the updates
     */
    default Entity<T> assertAndApply(Collection<?> updates) {
        Entity<T> result = this;
        for (Object event : updates) {
            result = result.assertAndApply(event);
        }
        return result;
    }

    /**
     * Determines the possible target entities for the given update. The update can be a payload or an instance of
     * {@link HasMessage}.
     * <p>
     * The method evaluates the update against child entities of this entity and identifies which entities are valid
     * targets.
     *
     * @param update the object to evaluate against the entities. If null, an empty list is returned.
     * @return an iterable collection of entities that are considered valid targets for the provided update.
     */
    default Iterable<Entity<?>> possibleTargets(Object update) {
        if (update == null) {
            return emptyList();
        }
        for (Entity<?> e : entities()) {
            if (e.isPossibleTarget(update, false)) {
                return singletonList(e);
            }
        }
        List<Entity<?>> result = new ArrayList<>();
        for (Entity<?> e : entities()) {
            if (e.isPossibleTarget(update, true)) {
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

    /**
     * Calculates the depth level of the current entity in the aggregate. The depth is determined by counting the number
     * of parent entities above the current entity. The root entity has a depth of 0.
     *
     * @return the depth level of the current entity, where 0 indicates the root entity.
     */
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
        Object payload = message instanceof HasMessage hm ? hm.getPayload() : message;
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
}