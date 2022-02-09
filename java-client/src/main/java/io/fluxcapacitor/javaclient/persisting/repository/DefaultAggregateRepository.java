package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ModifiableAggregateRoot;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.Aggregate;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.AggregateEventStream;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.fluxcapacitor.common.ObjectUtils.memoize;
import static io.fluxcapacitor.common.ObjectUtils.safelySupply;
import static java.util.Optional.ofNullable;

@Slf4j
@AllArgsConstructor
@Getter(AccessLevel.PRIVATE)
@Accessors(fluent = true)
public class DefaultAggregateRepository implements AggregateRepository {
    private final EventStore eventStore;
    private final SnapshotStore snapshotStore;
    private final Cache cache;
    private final DocumentStore documentStore;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final EventSourcingHandlerFactory handlerFactory;

    private final Function<Class<?>, AnnotatedAggregateRepository<?>> delegates = memoize(
            type -> new AnnotatedAggregateRepository<>(type, serializer(), cache(), eventStore(), snapshotStore(),
                                                       dispatchInterceptor(), handlerFactory(),
                                                       documentStore()));

    @Override
    @SuppressWarnings("unchecked")
    public <T> AggregateRoot<T> load(String aggregateId, Class<T> type) {
        return (AggregateRoot<T>) delegates.apply(type).load(aggregateId);
    }

    @Override
    public boolean cachingAllowed(Class<?> type) {
        return delegates.apply(type).isCached();
    }

    public static class AnnotatedAggregateRepository<T> {
        private final Class<T> type;
        private final Cache cache;
        private final EventSourcingHandler<T> eventSourcingHandler;
        private final boolean eventSourced;
        private final boolean commitInBatch;
        private final SnapshotTrigger snapshotTrigger;
        private final SnapshotStore snapshotStore;
        private final boolean searchable;
        private final String collection;
        private final Function<AggregateRoot<?>, Instant> timestampFunction;
        private final Serializer serializer;
        private final EventStore eventStore;
        private final DispatchInterceptor dispatchInterceptor;
        private final DocumentStore documentStore;

        public AnnotatedAggregateRepository(Class<T> type, Serializer serializer, Cache cache, EventStore eventStore,
                                            SnapshotStore snapshotStore,
                                            DispatchInterceptor dispatchInterceptor,
                                            EventSourcingHandlerFactory handlerFactory, DocumentStore documentStore) {
            this.serializer = serializer;
            this.eventStore = eventStore;
            this.dispatchInterceptor = dispatchInterceptor;
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
            this.eventSourcingHandler = handlerFactory.forType(type);
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
                    .filter(s -> !s.isBlank()).<Function<AggregateRoot<?>, Instant>>map(
                            s -> aggregateRoot -> ReflectionUtils.readProperty(s, aggregateRoot.get())
                                    .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                                        if (warnedAboutMissingTimePath.compareAndSet(false, true)) {
                                            log.warn("Aggregate type {} does not declare a timestamp property at '{}'",
                                                     aggregateRoot.get().getClass().getSimpleName(), s);
                                        }
                                        return aggregateRoot.timestamp();
                                    }))
                    .orElse(AggregateRoot::timestamp);
        }

        protected ModifiableAggregateRoot<T> load(String id) {
            return ModifiableAggregateRoot.load(id, () -> {
                ImmutableAggregateRoot<T> delegate =
                        Optional.<ImmutableAggregateRoot<T>>ofNullable(cache.getIfPresent(id))
                                .filter(a -> a.get() == null || type.isAssignableFrom(a.get().getClass()))
                                .orElseGet(() -> {
                                    var builder =
                                            ImmutableAggregateRoot.<T>builder().id(id).type(type)
                                                    .eventSourcingHandler(eventSourcingHandler).serializer(serializer);
                                    ImmutableAggregateRoot<T> model =
                                            (searchable && !eventSourced
                                                    ? documentStore.<T>fetchDocument(id, collection)
                                                    .map(d -> builder.value(d).build())
                                                    : snapshotStore.<T>getSnapshot(id).map(
                                                    a -> ImmutableAggregateRoot.from(a, eventSourcingHandler,
                                                                                     serializer)))
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
                                                    }).orElseGet(builder::build);
                                    if (!eventSourced) {
                                        return model;
                                    }
                                    AggregateEventStream<DeserializingMessage> eventStream
                                            = eventStore.getEvents(id, model.sequenceNumber());
                                    Iterator<DeserializingMessage> iterator = eventStream.iterator();
                                    while (iterator.hasNext()) {
                                        model = model.apply(iterator.next());
                                    }
                                    return model.toBuilder().sequenceNumber(
                                            eventStream.getLastSequenceNumber().orElse(model.sequenceNumber())).build();
                                });
                return delegate.get() != null ? cache.computeIfAbsent(id, i -> delegate) : delegate;
            }, commitInBatch, serializer, dispatchInterceptor, this::commit);
        }

        protected void commit(ImmutableAggregateRoot<?> model, List<DeserializingMessage> unpublishedEvents,
                              String initialEventId) {
            try {
                cache.<AggregateRoot<?>>compute(model.id(), (id, current) ->
                        current == null || Objects.equals(initialEventId, current.lastEventId())
                        || unpublishedEvents.isEmpty() ? model : current.apply(unpublishedEvents));
                if (!unpublishedEvents.isEmpty()) {
                    eventStore.storeEvents(model.id(), new ArrayList<>(unpublishedEvents)).awaitSilently();
                    if (snapshotTrigger.shouldCreateSnapshot(model, unpublishedEvents)) {
                        snapshotStore.storeSnapshot(model);
                    }
                }
                if (searchable) {
                    Object value = model.get();
                    if (value == null) {
                        documentStore.deleteDocument(model.id(), collection);
                    } else {
                        documentStore.index(value, model.id(), collection, timestampFunction.apply(model));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to commit aggregate {}", model.id(), e);
                cache.invalidate(model.id());
            }
        }

        protected boolean isCached() {
            return !(cache instanceof NoOpCache);
        }
    }
}
