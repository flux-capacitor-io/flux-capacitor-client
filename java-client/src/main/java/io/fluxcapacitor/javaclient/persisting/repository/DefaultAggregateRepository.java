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

package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.api.modeling.RepairRelationships;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AppliedEvent;
import io.fluxcapacitor.javaclient.modeling.DefaultEntityHelper;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityHelper;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.EventPublication;
import io.fluxcapacitor.javaclient.modeling.EventPublicationStrategy;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ModifiableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.NoOpEntity;
import io.fluxcapacitor.javaclient.modeling.SideEffectFreeEntity;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingException;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.NoOpSnapshotStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.NoSnapshotTrigger;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.PeriodicSnapshotTrigger;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotTrigger;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.fluxcapacitor.common.Guarantee.STORED;
import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.classForName;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.hasProperty;
import static io.fluxcapacitor.javaclient.modeling.ModifiableAggregateRoot.getActiveAggregatesFor;
import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;


/**
 * Default implementation of the {@link AggregateRepository} interface.
 * <p>
 * This class supports aggregates that are either event-sourced or document-based. Its behavior is driven by the
 * {@link Aggregate @Aggregate} annotation on the aggregate class, which determines aspects such as:
 * <ul>
 *   <li>Whether the aggregate is event-sourced or backed by a document store.</li>
 *   <li>If snapshots should be used and their frequency (via {@code snapshotPeriod}).</li>
 *   <li>Whether the aggregate is searchable (indexed for queries).</li>
 *   <li>Whether events should be published and how (via {@link EventPublicationStrategy}).</li>
 * </ul>
 *
 * <p>It supports caching of aggregates and entity-aggregate relationship metadata via the provided {@link Cache} instances.
 * These are consulted and updated during load and commit operations.
 *
 * <p>This repository coordinates with several components to manage aggregates:
 * <ul>
 *   <li>{@link EventStore} and {@link EventStoreClient} for storing and loading events.</li>
 *   <li>{@link SnapshotStore} for loading and storing snapshots.</li>
 *   <li>{@link DocumentStore} for searchable aggregates (non-event-sourced).</li>
 *   <li>{@link DispatchInterceptor} to inspect or modify outgoing events before dispatch.</li>
 * </ul>
 *
 * <p>This implementation also tracks relationships between aggregates and nested entities (e.g., value objects or sub-entities)
 * and can repair these links after structural refactors using {@link #repairRelationships(Entity)}.
 *
 * <p>The inner {@code AnnotatedAggregateRepository<T>} class handles aggregate-specific operations by introspecting the
 * annotations and metadata declared on a given aggregate type.
 *
 * @see Aggregate
 * @see AggregateRepository
 * @see Entity
 */
@Slf4j
@AllArgsConstructor
@Getter(AccessLevel.PRIVATE)
@Accessors(fluent = true)
public class DefaultAggregateRepository implements AggregateRepository {
    private final EventStore eventStore;
    private final EventStoreClient eventStoreClient;
    private final SnapshotStore snapshotStore;
    private final Cache aggregateCache;
    private final Cache relationshipsCache;
    private final DocumentStore documentStore;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final EntityHelper entityHelper;

    private final Function<Class<?>, AnnotatedAggregateRepository<?>> delegates =
            memoize(AnnotatedAggregateRepository::new);

    @Override
    @SuppressWarnings("unchecked")
    public <T> Entity<T> load(@NonNull Object aggregateId, Class<T> type) {
        Class<?> knownType;
        if (Object.class.equals(type)) {
            knownType = getAggregatesFor(aggregateId).getOrDefault(aggregateId.toString(), Object.class);
        } else {
            knownType = type;
        }
        if (Entity.isLoading()) {
            return new NoOpEntity<>(() -> (Entity<T>) delegates.apply(knownType).load(aggregateId));
        }
        return (Entity<T>) delegates.apply(knownType).load(aggregateId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Entity<T> loadFor(@NonNull Object entityId, Class<?> defaultType) {
        Map<String, Class<?>> aggregates = getAggregatesFor(entityId);
        if (aggregates.isEmpty()) {
            return (Entity<T>) load(entityId, defaultType);
        }
        if (aggregates.containsKey(entityId.toString())) {
            Entity<T> result = (Entity<T>) load(entityId, aggregates.get(entityId.toString()));
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (aggregates.size() > 1) {
            log.debug("Found multiple aggregates containing entity {}. Loading the most recent one.", entityId);
        }
        return aggregates.entrySet().stream().filter(e -> !Void.class.equals(e.getValue()))
                .reduce((a, b) -> b).map(e -> (Entity<T>) load(e.getKey(), e.getValue()))
                .orElseGet(() -> (Entity<T>) load(entityId, defaultType));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Entity<T> asEntity(T entityValue) {
        AnnotatedAggregateRepository<T> repository = (AnnotatedAggregateRepository<T>) delegates.apply(
                entityValue == null ? Object.class : entityValue.getClass());
        return repository.fromValue(entityValue);
    }

    @Override
    @SuppressWarnings("Java8MapForEach")
    public Map<String, Class<?>> getAggregatesFor(@NonNull Object entityId) {
        LinkedHashMap<String, Class<?>> result = new LinkedHashMap<>(getActiveAggregatesFor(entityId));
        relationshipsCache.computeIfAbsent(
                        entityId.toString(), id -> eventStoreClient.getAggregatesFor(id.toString())
                                .entrySet().stream().collect(toMap(Map.Entry::getKey, e -> classForName(
                                        serializer.upcastType(e.getValue()), Void.class), (a, b) -> b, LinkedHashMap::new)))
                .entrySet().forEach(e -> result.putIfAbsent(e.getKey(), e.getValue()));
        return result;
    }

    @Override
    public CompletableFuture<Void> deleteAggregate(Object aggregateId) {
        var type = getAggregatesFor(aggregateId).getOrDefault(aggregateId.toString(), Void.class);
        return delegates.apply(type).delete(aggregateId);
    }

    @Override
    public CompletableFuture<Void> repairRelationships(Entity<?> aggregate) {
        aggregate = aggregate.root();
        return eventStoreClient.repairRelationships(new RepairRelationships(
                aggregate.id().toString(), aggregate.type().getName(),
                aggregate.relationships().stream().map(Relationship::getEntityId).collect(toSet()), STORED));
    }

    /**
     * Aggregate-type-specific delegate used internally by {@link DefaultAggregateRepository}.
     * <p>
     * This class is instantiated per aggregate class and parses metadata from the {@link Aggregate} annotation to determine:
     * <ul>
     *   <li>Whether the aggregate is event-sourced or backed by the document store.</li>
     *   <li>If and how frequently snapshots should be created.</li>
     *   <li>Whether the aggregate is indexed for search.</li>
     *   <li>How events should be published and committed.</li>
     *   <li>Which cache(s) to use.</li>
     * </ul>
     *
     * <p>It provides loading and committing capabilities tailored to the configured storage and publication strategy.
     * It also supports refetching aggregates from the Flux platform and maintaining relationships with child entities.
     *
     * <p>Snapshot and document-based aggregates are supported out of the box. Event replay is triggered as needed.
     *
     * <p>This class is not intended to be used directly outside the {@code DefaultAggregateRepository}.
     *
     * @param <T> the aggregate root type
     */
    public class AnnotatedAggregateRepository<T> {

        private final Class<T> type;
        private final Cache aggregateCache;
        private final Cache relationshipsCache;
        private final boolean eventSourced;
        private final boolean commitInBatch;
        private final EventPublication eventPublication;
        private final EventPublicationStrategy publicationStrategy;
        private final SnapshotTrigger snapshotTrigger;
        private final SnapshotStore snapshotStore;
        private final boolean searchable;
        private final String collection;
        private final Function<Entity<?>, Instant> timestampFunction;
        private final Function<Entity<?>, Instant> endFunction;
        private final String idProperty;
        private final boolean ignoreUnknownEvents;

        public AnnotatedAggregateRepository(Class<T> type) {
            this.type = type;

            Aggregate annotation = DefaultEntityHelper.getRootAnnotation(type);
            this.aggregateCache = annotation.cached()
                    ? DefaultAggregateRepository.this.aggregateCache : NoOpCache.INSTANCE;
            this.relationshipsCache = annotation.cached()
                    ? DefaultAggregateRepository.this.relationshipsCache : NoOpCache.INSTANCE;
            this.eventSourced = annotation.eventSourced();
            this.commitInBatch = annotation.commitInBatch();
            this.eventPublication = annotation.eventPublication();
            this.publicationStrategy = annotation.publicationStrategy();
            int snapshotPeriod = annotation.eventSourced() || annotation.searchable() ? annotation.snapshotPeriod() : 1;
            this.snapshotTrigger = snapshotPeriod > 0 ? new PeriodicSnapshotTrigger(snapshotPeriod) :
                    NoSnapshotTrigger.INSTANCE;
            this.snapshotStore = snapshotPeriod > 0
                    ? DefaultAggregateRepository.this.snapshotStore : NoOpSnapshotStore.INSTANCE;
            this.searchable = annotation.searchable();
            this.collection = Optional.of(annotation).map(Aggregate::collection)
                    .filter(s -> !s.isEmpty()).orElse(type.getSimpleName());
            this.idProperty = getAnnotatedProperty(type, EntityId.class).map(ReflectionUtils::getName).orElse(null);
            AtomicBoolean warnedAboutMissingTimePath = new AtomicBoolean();
            this.timestampFunction = Optional.of(annotation).map(Aggregate::timestampPath)
                    .filter(s -> !s.isBlank()).<Function<Entity<?>, Instant>>map(
                            s -> aggregateRoot -> ReflectionUtils.readProperty(s, aggregateRoot.get())
                                    .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                                        if (aggregateRoot.isPresent()) {
                                            if (hasProperty(s, aggregateRoot.get())) {
                                                return null;
                                            }
                                            if (warnedAboutMissingTimePath.compareAndSet(false, true)) {
                                                log.warn(
                                                        "Aggregate type {} does not declare a timestamp property at '{}'",
                                                        aggregateRoot.get().getClass().getSimpleName(), s);
                                            }
                                        }
                                        return aggregateRoot.timestamp();
                                    }))
                    .orElse(Entity::timestamp);
            AtomicBoolean warnedAboutMissingEndPath = new AtomicBoolean();
            this.endFunction = Optional.of(annotation).map(Aggregate::endPath)
                    .filter(s -> !s.isBlank()).<Function<Entity<?>, Instant>>map(
                            s -> aggregateRoot -> ReflectionUtils.readProperty(s, aggregateRoot.get())
                                    .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                                        if (aggregateRoot.isPresent()) {
                                            if (hasProperty(s, aggregateRoot.get())) {
                                                return null;
                                            }
                                            if (warnedAboutMissingEndPath.compareAndSet(false, true)) {
                                                log.warn(
                                                        "Aggregate type {} does not declare an end timestamp property at '{}'",
                                                        aggregateRoot.get().getClass().getSimpleName(), s);
                                            }
                                        }
                                        return aggregateRoot.timestamp();
                                    }))
                    .orElse(timestampFunction);
            this.ignoreUnknownEvents = annotation.ignoreUnknownEvents();
        }

        @SuppressWarnings("unchecked")
        public Entity<T> fromValue(T value) {
            return new SideEffectFreeEntity<>(ImmutableAggregateRoot
                                                      .<T>builder()
                                                      .idProperty(idProperty)
                                                      .id(ReflectionUtils.readProperty(idProperty, value).orElse(null))
                                                      .value(value)
                                                      .type((Class<T>) (value != null ? value.getClass() :
                                                              Object.class))
                                                      .timestamp(FluxCapacitor.currentTime())
                                                      .entityHelper(entityHelper)
                                                      .eventStore(eventStore)
                                                      .serializer(serializer)
                                                      .sequenceNumber(0)
                                                      .build());
        }

        public CompletableFuture<Void> delete(Object id) {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            String aggregateId = id.toString();
            aggregateCache.remove(aggregateId);
            relationshipsCache.<Map<String, String>>modifyEach((entityId, map) -> {
                map.remove(aggregateId);
                return map.isEmpty() ? null : map;
            });
            futures.add(eventStoreClient.repairRelationships(
                    new RepairRelationships(aggregateId, type.getName(), Collections.emptySet(), STORED)));
            futures.add(eventStoreClient.deleteEvents(aggregateId, STORED));
            futures.add(snapshotStore.deleteSnapshot(id));
            if (searchable) {
                futures.add(documentStore.deleteDocument(id, collection));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        }

        public Entity<T> load(Object id) {
            return ModifiableAggregateRoot.load(
                    id, () -> aggregateCache.compute(id.toString(), (stringId, v) -> {
                        if (v != null) {
                            if (type.isAssignableFrom(v.type())) {
                                return v;
                            }
                            if (v.isPresent()) {
                                log.warn("Cache already contains a non-empty aggregate with id {} of type {} "
                                         + "that is not assignable to requested type {}. "
                                         + "This is likely to cause issues. Loading the aggregate again..",
                                         id, v.type(), type);
                            }
                        }
                        return eventSourceModel(loadSnapshot(id));
                    }), commitInBatch, eventPublication, publicationStrategy,
                    entityHelper, serializer, dispatchInterceptor, this::commit);
        }

        protected Entity<T> loadSnapshot(Object id) {
            var builder =
                    ImmutableAggregateRoot.<T>builder().id(id).type(type)
                            .idProperty(idProperty)
                            .entityHelper(entityHelper).serializer(serializer)
                            .eventStore(eventStore);
            return (searchable && !eventSourced
                    ? documentStore.<T>fetchDocument(id, collection)
                    .map(d -> builder.value(d).build())
                    : snapshotStore.<T>getSnapshot(id).map(
                    a -> ImmutableAggregateRoot.from(a, entityHelper, serializer, eventStore)))
                    .filter(a -> {
                        boolean assignable =
                                a.get() == null
                                || type.isAssignableFrom(a.get().getClass());
                        if (!assignable) {
                            log.warn(
                                    "Stored aggregate snapshot with id {} of type {} is not"
                                    + " assignable to the requested type {}. Ignoring the snapshot.",
                                    id, a.type(), type);
                        }
                        return assignable;
                    })
                    .map(a -> (Entity<T>) a)
                    .orElseGet(builder::build);
        }

        @SuppressWarnings("unchecked")
        protected Entity<T> eventSourceModel(Entity<T> model) {
            try {
                if (eventSourced) {
                    AggregateEventStream<DeserializingMessage> eventStream
                            = eventStore.getEvents(model.id().toString(), model.sequenceNumber(), -1,
                                                   ignoreUnknownEvents);
                    Iterator<DeserializingMessage> iterator = eventStream.iterator();
                    boolean wasLoading = Entity.isLoading();
                    try {
                        Entity.loading.set(true);
                        while (iterator.hasNext()) {
                            DeserializingMessage next = iterator.next();
                            if (model.isEmpty()) {
                                var t = Entity.getAggregateType(next);
                                if (t != null && !t.equals(this.type) && this.type.isAssignableFrom(t)) {
                                    model = model.withType((Class<T>) t);
                                }
                            }
                            try {
                                model = model.apply(next);
                            } catch (Throwable e) {
                                throw new EventSourcingException(format(
                                        "Failed to apply event %s to aggregate %s.", next.getIndex(), model.id()), e);
                            }
                        }
                    } finally {
                        Entity.loading.set(wasLoading);
                    }
                    model = model.withSequenceNumber(
                            eventStream.getLastSequenceNumber().orElse(model.sequenceNumber()));
                }
                return model;
            } catch (EventSourcingException e) {
                throw e;
            } catch (Throwable e) {
                throw new EventSourcingException("Failed to apply events to aggregate " + model.id(), e);
            }
        }

        public void commit(Entity<?> after, List<AppliedEvent> unpublishedEvents, Entity<?> before) {
            if (after.type() != null && !Objects.equals(after.type(), type)) {
                delegates.apply(after.type()).commit(after, unpublishedEvents, before);
                return;
            }
            try {
                aggregateCache.<Entity<?>>compute(after.id().toString(), (stringId, current) ->
                        current == null || Objects.equals(before.lastEventId(), current.lastEventId())
                        || unpublishedEvents.isEmpty() ? after : current.apply(
                                unpublishedEvents.stream().map(AppliedEvent::getEvent).toList()));
                Set<Relationship> associations = after.associations(before), dissociations =
                        after.dissociations(before);
                dissociations.forEach(
                        r -> relationshipsCache.<Map<String, String>>computeIfPresent(r.getEntityId(), (id, map) -> {
                            map.remove(r.getAggregateId());
                            return map;
                        }));
                associations.forEach(
                        r -> relationshipsCache.<Map<String, Class<?>>>computeIfPresent(r.getEntityId(), (id, map) -> {
                            map.put(r.getAggregateId(), after.type());
                            return map;
                        }));
                if (!associations.isEmpty() || !dissociations.isEmpty()) {
                    eventStoreClient.updateRelationships(
                            new UpdateRelationships(associations, dissociations, STORED)).get();
                }
                if (!unpublishedEvents.isEmpty()) {
                    storeEvents(after.id().toString(), unpublishedEvents);
                    if (snapshotTrigger.shouldCreateSnapshot(after, unpublishedEvents)) {
                        snapshotStore.storeSnapshot(after);
                    }
                }
                if (searchable) {
                    Object value = after.get();
                    if (value == null) {
                        documentStore.deleteDocument(after.id().toString(), collection);
                    } else {
                        documentStore.index(
                                value, after.id().toString(), collection,
                                timestampFunction.apply(after), endFunction.apply(after)).get();
                    }
                }
            } catch (Exception e) {
                log.error("Failed to commit aggregate {}", after.id(), e);
                aggregateCache.remove(after.id().toString());
            }
        }

        @SneakyThrows
        void storeEvents(String aggregateId, List<AppliedEvent> appliedEvents) {
            FluxCapacitor.getOptionally().ifPresent(fc -> appliedEvents.forEach(
                    e -> e.getEvent().getSerializedObject().setSource(fc.client().id())));

            List<AppliedEvent> currentBatch = new ArrayList<>();
            EventPublicationStrategy currentStrategy = null;
            for (AppliedEvent event : appliedEvents) {
                if (event.getPublicationStrategy() != currentStrategy && currentStrategy != null) {
                    eventStore.storeEvents(aggregateId, currentBatch.stream().map(AppliedEvent::getEvent).toList(),
                                           currentStrategy).get();
                    currentBatch.clear();
                }
                currentStrategy = event.getPublicationStrategy();
                currentBatch.add(event);
            }
            if (!currentBatch.isEmpty()) {
                eventStore.storeEvents(aggregateId, currentBatch.stream().map(AppliedEvent::getEvent).toList(),
                                       currentStrategy).get();
            }
        }
    }
}
