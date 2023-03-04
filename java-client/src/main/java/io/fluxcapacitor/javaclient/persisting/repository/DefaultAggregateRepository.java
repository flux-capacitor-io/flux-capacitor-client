package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.DefaultEntityHelper;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.EntityHelper;
import io.fluxcapacitor.javaclient.modeling.EntityId;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ModifiableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.NoOpEntity;
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
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
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
import static io.fluxcapacitor.common.reflection.ReflectionUtils.getAnnotatedProperty;

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
    private final EntityHelper entityHelper;

    private final Function<Class<?>, AnnotatedAggregateRepository<?>> delegates = memoize(
            type -> new AnnotatedAggregateRepository<>(type, serializer(), cache(), relationshipsCache(),
                                                       eventStore(), snapshotStore(),
                                                       dispatchInterceptor(), entityHelper(), documentStore()));

    @Override
    @SuppressWarnings("unchecked")
    public <T> Entity<T> load(@NonNull String aggregateId, Class<T> type) {
        if (Entity.isLoading()) {
            return new NoOpEntity<>(() -> (Entity<T>) delegates.apply(type).load(aggregateId));
        }
        return (Entity<T>) delegates.apply(type).load(aggregateId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Entity<T> loadFor(@NonNull String entityId, Class<?> defaultType) {
        Map<String, Class<?>> aggregates =
                relationshipsCache.computeIfAbsent(entityId, id -> eventStore.getAggregatesFor(entityId));
        if (aggregates.isEmpty()) {
            return (Entity<T>) load(entityId, defaultType);
        }
        if (aggregates.containsKey(entityId)) {
            return (Entity<T>) load(entityId, aggregates.get(entityId));
        }
        if (aggregates.size() > 1) {
            log.info("Found multiple aggregates containing entity {}. Loading the most recent one.", entityId);
        }
        return aggregates.entrySet().stream().filter(e -> !Void.class.equals(e.getValue()))
                .reduce((a, b) -> b).map(e -> (Entity<T>) load(e.getKey(), e.getValue()))
                .orElseGet(() -> (Entity<T>) load(entityId, defaultType));
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
        private final EntityHelper entityHelper;
        private final DocumentStore documentStore;
        private final String idProperty;
        private boolean ignoreUnknownEvents;

        public AnnotatedAggregateRepository(Class<T> type, Serializer serializer, Cache cache, Cache relationshipsCache,
                                            EventStore eventStore, SnapshotStore snapshotStore,
                                            DispatchInterceptor dispatchInterceptor,
                                            EntityHelper entityHelper, DocumentStore documentStore) {
            this.serializer = serializer;
            this.relationshipsCache = relationshipsCache;
            this.eventStore = eventStore;
            this.dispatchInterceptor = dispatchInterceptor;
            this.entityHelper = entityHelper;
            this.documentStore = documentStore;
            this.type = type;

            Aggregate annotation = DefaultEntityHelper.getRootAnnotation(type);
            this.cache = annotation.cached() ? cache : NoOpCache.INSTANCE;
            this.eventSourced = annotation.eventSourced();
            this.commitInBatch = annotation.commitInBatch();
            int snapshotPeriod = annotation.eventSourced() || annotation.searchable() ? annotation.snapshotPeriod() : 1;
            this.snapshotTrigger = snapshotPeriod > 0 ? new PeriodicSnapshotTrigger(snapshotPeriod) :
                    NoSnapshotTrigger.INSTANCE;
            this.snapshotStore = snapshotPeriod > 0 ? snapshotStore : NoOpSnapshotStore.INSTANCE;
            this.searchable = annotation.searchable();
            this.collection = Optional.of(annotation).map(Aggregate::collection)
                    .filter(s -> !s.isEmpty()).orElse(type.getSimpleName());
            this.idProperty = getAnnotatedProperty(type, EntityId.class).map(ReflectionUtils::getName).orElse(null);
            AtomicBoolean warnedAboutMissingTimePath = new AtomicBoolean();
            this.timestampFunction = Optional.of(annotation).map(Aggregate::timestampPath)
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
            this.ignoreUnknownEvents = annotation.ignoreUnknownEvents();
        }

        public ModifiableAggregateRoot<T> load(String id) {
            return ModifiableAggregateRoot.load(
                    id, () -> cache.<ImmutableAggregateRoot<T>>compute(id, (k, v) -> {
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
                    }), commitInBatch, serializer, dispatchInterceptor, this::commit);
        }

        protected ImmutableAggregateRoot<T> loadSnapshot(String id) {
            var builder =
                    ImmutableAggregateRoot.<T>builder().id(id).type(type)
                            .idProperty(idProperty)
                            .entityHelper(entityHelper).serializer(serializer);
            return (searchable && !eventSourced
                    ? documentStore.<T>fetchDocument(id, collection)
                    .map(d -> builder.value(d).build())
                    : snapshotStore.<T>getSnapshot(id).map(
                    a -> ImmutableAggregateRoot.from(a, entityHelper, serializer)))
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
                    .map(a -> (ImmutableAggregateRoot<T>) a)
                    .orElseGet(builder::build);
        }

        protected ImmutableAggregateRoot<T> eventSourceModel(ImmutableAggregateRoot<T> model) {
            try {
                if (eventSourced) {
                    AggregateEventStream<DeserializingMessage> eventStream
                            = eventStore.getEvents(model.id().toString(), model.sequenceNumber(), ignoreUnknownEvents);
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
                    model = model.toBuilder().sequenceNumber(
                            eventStream.getLastSequenceNumber().orElse(model.sequenceNumber())).build();
                }
                return model;
            } catch (EventSourcingException e) {
                throw e;
            } catch (Throwable e) {
                throw new EventSourcingException("Failed to apply events to aggregate " + model.id(), e);
            }
        }

        public void commit(Entity<?> after, List<DeserializingMessage> unpublishedEvents, Entity<?> before) {
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
                cache.remove(after.id());
            }
        }

        protected boolean isCached() {
            return !(cache instanceof NoOpCache);
        }
    }
}
