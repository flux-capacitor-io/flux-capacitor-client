package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.EntityMatcher;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ModifiableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.NoOpEntity;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.NoOpSnapshotStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.NoSnapshotTrigger;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.PeriodicSnapshotTrigger;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotStore;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.SnapshotTrigger;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.ObjectUtils.safelySupply;
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;
import static java.util.Optional.ofNullable;

@Slf4j
@AllArgsConstructor
@Getter(AccessLevel.PRIVATE)
@Accessors(fluent = true)
public class DefaultAggregateRepository implements AggregateRepository {
    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;
    private final Cache cache;
    private final Cache relationshipsCache;
    private final DocumentStore documentStore;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final EntityMatcher entityMatcher;

    private final Function<Class<?>, AnnotatedAggregateRepository<?>> delegates = memoize(
            type -> new AnnotatedAggregateRepository<>(type, serializer(), cache(), relationshipsCache(),
                                                       eventStore(), snapshotStore(),
                                                       dispatchInterceptor(), entityMatcher(), documentStore()));

    @Override
    @SuppressWarnings("unchecked")
    public <T> Entity<T> load(String aggregateId, Class<T> type) {
        if (Entity.isLoading()) {
            return new NoOpEntity<>(() -> (Entity<T>) delegates.apply(type).load(aggregateId));
        }
        return (Entity<T>) delegates.apply(type).load(aggregateId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Entity<T> loadFor(String entityId, Class<?> defaultType) {
        Map<String, Class<?>> aggregates = getAggregatesFor(entityId);
        if (aggregates.isEmpty()) {
            return (Entity<T>) load(entityId, defaultType);
        }
        if (aggregates.containsKey(entityId)) {
            return (Entity<T>) load(entityId, aggregates.get(entityId));
        }
        if (aggregates.size() > 1) {
            log.warn("Found several aggregates containing entity {}", entityId);
        }
        return aggregates.entrySet().stream().filter(e -> !Void.class.equals(e.getValue())).findFirst()
                .map(e -> (Entity<T>) load(e.getKey(), e.getValue()))
                .orElseGet(() -> (Entity<T>) load(entityId, defaultType));
    }

    @Override
    public Map<String, Class<?>> getAggregatesFor(String entityId) {
        return relationshipsCache.computeIfAbsent(entityId, id -> eventStore.getAggregatesFor(entityId));
    }

    @Override
    public boolean cachingAllowed(Class<?> type) {
        return delegates.apply(type).isCached();
    }

    public static class AnnotatedAggregateRepository<T> {
        private final Class<T> type;
        private final Cache cache;
        private final Cache relationshipsCache;
        private final boolean eventSourced;
        private final boolean commitInBatch;
        private final SnapshotTrigger snapshotTrigger;
        private final SnapshotStore snapshotStore;
        private final boolean searchable;
        private final String collection;
        private final Function<Entity<?>, Instant> timestampFunction;
        private final Serializer serializer;
        private final EventStore eventStore;
        private final DispatchInterceptor dispatchInterceptor;
        private final EntityMatcher entityMatcher;
        private final DocumentStore documentStore;
        private final String idProperty;

        public AnnotatedAggregateRepository(Class<T> type, Serializer serializer, Cache cache, Cache relationshipsCache,
                                            EventStore eventStore, SnapshotStore snapshotStore,
                                            DispatchInterceptor dispatchInterceptor,
                                            EntityMatcher entityMatcher, DocumentStore documentStore) {
            this.serializer = serializer;
            this.relationshipsCache = relationshipsCache;
            this.eventStore = eventStore;
            this.dispatchInterceptor = dispatchInterceptor;
            this.entityMatcher = entityMatcher;
            this.documentStore = documentStore;
            Aggregate typeAnnotation = ReflectionUtils.getTypeAnnotation(type, Aggregate.class);
            int snapshotPeriod = ofNullable(typeAnnotation)
                    .map(a -> a.eventSourced() || a.searchable() ? a.snapshotPeriod() : 1).orElseGet(safelySupply(
                            () -> (int) Aggregate.class.getMethod("snapshotPeriod").getDefaultValue()));
            AtomicBoolean warnedAboutMissingTimePath = new AtomicBoolean();
            this.type = type;
            this.cache = ofNullable(typeAnnotation).map(Aggregate::cached).orElseGet(
                    safelySupply(() -> (boolean) Aggregate.class.getMethod("cached").getDefaultValue()))
                    ? cache : NoOpCache.INSTANCE;
            this.eventSourced = ofNullable(typeAnnotation).map(Aggregate::eventSourced)
                    .orElseGet(safelySupply(() -> (boolean) Aggregate.class.getMethod(
                            "eventSourced").getDefaultValue()));
            this.commitInBatch = ofNullable(typeAnnotation).map(Aggregate::commitInBatch).orElseGet(safelySupply(
                    () -> (boolean) Aggregate.class.getMethod("commitInBatch").getDefaultValue()));
            this.snapshotTrigger = snapshotPeriod > 0 ? new PeriodicSnapshotTrigger(snapshotPeriod) :
                    NoSnapshotTrigger.INSTANCE;
            this.snapshotStore = snapshotPeriod > 0 ? snapshotStore : NoOpSnapshotStore.INSTANCE;
            this.searchable = ofNullable(typeAnnotation).map(Aggregate::searchable)
                    .orElseGet(safelySupply(() -> (boolean) Aggregate.class.getMethod("searchable").getDefaultValue()));
            this.collection = ofNullable(typeAnnotation).map(Aggregate::collection)
                    .filter(s -> !s.isEmpty()).orElse(type.getSimpleName());
            this.timestampFunction = ofNullable(typeAnnotation).map(Aggregate::timestampPath)
                    .filter(s -> !s.isBlank()).<Function<Entity<?>, Instant>>map(
                            s -> aggregateRoot -> ReflectionUtils.readProperty(s, aggregateRoot.get())
                                    .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                                        if (warnedAboutMissingTimePath.compareAndSet(false, true)) {
                                            log.warn("Aggregate type {} does not declare a timestamp property at '{}'",
                                                     aggregateRoot.get().getClass().getSimpleName(), s);
                                        }
                                        return aggregateRoot.timestamp();
                                    }))
                    .orElse(Entity::timestamp);
            this.idProperty = getAnnotatedProperty(type, EntityId.class).map(ReflectionUtils::getName).orElse(null);
        }

        protected ModifiableAggregateRoot<T> load(String id) {
            return ModifiableAggregateRoot.load(id, () -> {
                ImmutableAggregateRoot<T> delegate =
                        Optional.<ImmutableAggregateRoot<T>>ofNullable(cache.getIfPresent(id))
                                .filter(a -> a.get() == null || type.isAssignableFrom(a.get().getClass()))
                                .orElseGet(() -> {
                                    var builder =
                                            ImmutableAggregateRoot.<T>builder().id(id).type(type).idProperty(idProperty)
                                                    .entityMatcher(entityMatcher).serializer(serializer);
                                    ImmutableAggregateRoot<T> model =
                                            (searchable && !eventSourced
                                                    ? documentStore.<T>fetchDocument(id, collection)
                                                    .map(d -> builder.value(d).build())
                                                    : snapshotStore.<T>getSnapshot(id).map(
                                                    a -> ImmutableAggregateRoot.from(a, entityMatcher, serializer)))
                                                    .filter(a -> {
                                                        boolean assignable =
                                                                a.get() == null
                                                                || type.isAssignableFrom(a.get().getClass());
                                                        if (!assignable) {
                                                            log.warn("Could not load aggregate {} because the requested"
                                                                     + " type {} is not assignable to the stored type {}",
                                                                     id, type, a.get().getClass());
                                                        }
                                                        return assignable;
                                                    })
                                                    .map(a -> (ImmutableAggregateRoot<T>) a).orElseGet(builder::build);
                                    if (!eventSourced) {
                                        return model;
                                    }
                                    AggregateEventStream<DeserializingMessage> eventStream
                                            = eventStore.getEvents(id, model.sequenceNumber());
                                    Iterator<DeserializingMessage> iterator = eventStream.iterator();
                                    boolean wasLoading = Entity.isLoading();
                                    try {
                                        Entity.loading.set(true);
                                        while (iterator.hasNext()) {
                                            model = model.apply(iterator.next());
                                        }
                                    } finally {
                                        Entity.loading.set(wasLoading);
                                    }
                                    return model.toBuilder().sequenceNumber(
                                            eventStream.getLastSequenceNumber().orElse(model.sequenceNumber())).build();
                                });
                return delegate.get() != null ? cache.computeIfAbsent(id, i -> delegate) : delegate;
            }, commitInBatch, serializer, dispatchInterceptor, this::commit);
        }

        protected void commit(Entity<?> after, List<DeserializingMessage> unpublishedEvents, Entity<?> before) {
            try {
                cache.<Entity<?>>compute(after.id(), (id, current) ->
                        current == null || Objects.equals(before.lastEventId(), current.lastEventId())
                        || unpublishedEvents.isEmpty() ? after : current.apply(unpublishedEvents));
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
                eventStore.updateRelationships(associations, dissociations).awaitSilently();
                if (!unpublishedEvents.isEmpty()) {
                    FluxCapacitor.getOptionally().ifPresent(
                            fc -> unpublishedEvents.forEach(e -> e.getSerializedObject().setSource(fc.client().id())));
                    eventStore.storeEvents(after.id().toString(), new ArrayList<>(unpublishedEvents)).awaitSilently();
                    if (snapshotTrigger.shouldCreateSnapshot(after, unpublishedEvents)) {
                        snapshotStore.storeSnapshot(after);
                    }
                }
                if (searchable) {
                    Object value = after.get();
                    if (value == null) {
                        documentStore.deleteDocument(after.id().toString(), collection);
                    } else {
                        documentStore.index(value, after.id().toString(), collection, timestampFunction.apply(after));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to commit aggregate {}", after.id(), e);
                cache.invalidate(after.id());
            }
        }

        protected boolean isCached() {
            return !(cache instanceof NoOpCache);
        }
    }
}
