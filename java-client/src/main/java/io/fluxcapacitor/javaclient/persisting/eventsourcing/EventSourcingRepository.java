package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.COMMAND;
import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultParameterResolvers;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.whenBatchCompletes;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.whenMessageCompletes;
import static java.lang.String.format;
import static java.util.Collections.asLifoQueue;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Slf4j
@AllArgsConstructor
public class EventSourcingRepository implements AggregateRepository {
    private static final Function<String, String> keyFunction = aggregateId ->
            EventSourcingRepository.class.getSimpleName() + ":" + aggregateId;

    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final EventStoreSerializer serializer;
    private final EventSourcingHandlerFactory handlerFactory;

    private final Map<Class<?>, Function<String, EventSourcedAggregate<?>>> aggregateFactory =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Collection<EventSourcedAggregate<?>>> loadedModels = new ThreadLocal<>();

    public EventSourcingRepository(EventStore eventStore, SnapshotRepository snapshotRepository, Cache cache, EventStoreSerializer serializer) {
        this(eventStore, snapshotRepository, cache, serializer, 
             new DefaultEventSourcingHandlerFactory(defaultParameterResolvers));
    }

    @Override
    public boolean supports(Class<?> aggregateType) {
        return aggregateType.isAnnotationPresent(EventSourced.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean onlyCached) {
        if (onlyCached) {
            return Optional.<EventSourcedModel<T>>ofNullable(cache.getIfPresent(keyFunction.apply(aggregateId)))
                    .orElse(null);
        }
        
        if (loadedModels.get() == null) {
            if (ofNullable(DeserializingMessage.getCurrent()).map(m -> m.getMessageType() == COMMAND).isPresent()) {
                loadedModels.set(asLifoQueue(new ArrayDeque<>()));

                Runnable commit = () -> {
                    Collection<EventSourcedAggregate<?>> models = loadedModels.get();
                    loadedModels.remove();
                    models.forEach(EventSourcedAggregate::commit);
                };
                if (aggregateType.getAnnotation(EventSourced.class).commitInBatch()) {
                    whenBatchCompletes(commit);
                } else {
                    whenMessageCompletes(commit);
                }
            } else {
                return createAggregate(aggregateType, aggregateId);
            }
        }
        
        Collection<EventSourcedAggregate<?>> loaded = loadedModels.get();
        return loaded.stream().filter(model -> model.id.equals(aggregateId)).map(m -> (EventSourcedAggregate<T>) m)
                .findAny().orElseGet(() -> {
                    EventSourcedAggregate<T> model = createAggregate(aggregateType, aggregateId);
                    loaded.add(model);
                    return model;
                });
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcedAggregate<T> createAggregate(Class<T> aggregateType, String aggregateId) {
        return (EventSourcedAggregate<T>) aggregateFactory.computeIfAbsent(aggregateType, t -> {
            EventSourcingHandler<T> eventSourcingHandler = handlerFactory.forType(aggregateType);
            Cache cache = isCached(aggregateType) ? this.cache : NoOpCache.INSTANCE;
            SnapshotRepository snapshotRepository = snapshotRepository(aggregateType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(aggregateType);
            String domain = domain(aggregateType);
            return id -> {
                EventSourcedAggregate<T> eventSourcedAggregate = new EventSourcedAggregate<>(
                        aggregateType, eventSourcingHandler, cache, serializer, eventStore, snapshotRepository,
                        snapshotTrigger, domain, loadedModels.get() == null, id);
                eventSourcedAggregate.initialize();
                return eventSourcedAggregate;
            };
        }).apply(aggregateId);
    }

    @SneakyThrows
    protected SnapshotRepository snapshotRepository(Class<?> aggregateType) {
        int frequency =
                ofNullable(aggregateType.getAnnotation(EventSourced.class)).map(EventSourced::snapshotPeriod)
                        .orElse((int) EventSourced.class.getMethod("snapshotPeriod").getDefaultValue());
        return frequency > 0 ? this.snapshotRepository : NoOpSnapshotRepository.INSTANCE;
    }

    @SneakyThrows
    protected SnapshotTrigger snapshotTrigger(Class<?> aggregateType) {
        int frequency =
                ofNullable(aggregateType.getAnnotation(EventSourced.class)).map(EventSourced::snapshotPeriod)
                        .orElse((int) EventSourced.class.getMethod("snapshotPeriod").getDefaultValue());
        return frequency > 0 ? new PeriodicSnapshotTrigger(frequency) : NoSnapshotTrigger.INSTANCE;
    }

    @SneakyThrows
    protected boolean isCached(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(EventSourced.class)).map(EventSourced::cached)
                .orElse((boolean) EventSourced.class.getMethod("cached").getDefaultValue());
    }

    protected String domain(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(EventSourced.class)).map(EventSourced::domain)
                .filter(s -> !s.isEmpty()).orElse(aggregateType.getSimpleName());
    }

    @RequiredArgsConstructor
    protected static class EventSourcedAggregate<T> implements Aggregate<T> {

        private final Class<T> aggregateType;
        private final EventSourcingHandler<T> eventSourcingHandler;
        private final Cache cache;
        private final EventStoreSerializer serializer;
        private final EventStore eventStore;
        private final SnapshotRepository snapshotRepository;
        private final SnapshotTrigger snapshotTrigger;
        private final String domain;
        private final List<DeserializingMessage> unpublishedEvents = new ArrayList<>();
        private final boolean readOnly;
        private final String id;
        
        private EventSourcedModel<T> model;

        protected void initialize() {
            model = Optional.<EventSourcedModel<T>>ofNullable(cache.getIfPresent(keyFunction.apply(id)))
                    .orElseGet(() -> {
                        EventSourcedModel<T> model = snapshotRepository.<T>getSnapshot(id)
                                .orElse(EventSourcedModel.<T>builder().id(id).build());
                        for (DeserializingMessage event : eventStore.getDomainEvents(id, model.sequenceNumber())
                                .collect(toList())) {
                            model = model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                                    .lastEventId(event.getSerializedObject().getMessageId())
                                    .timestamp(Instant.ofEpochMilli(event.getSerializedObject().getTimestamp()))
                                    .model(eventSourcingHandler.invoke(model.get(), event)).build();
                        }
                        return model;
                    });
        }

        @Override
        public Aggregate<T> apply(Message eventMessage) {
            if (readOnly) {
                throw new UnsupportedOperationException(format("Not allowed to apply a %s. The model is readonly.",
                                                               eventMessage));
            }
            Metadata metadata = eventMessage.getMetadata();
            metadata.put(Aggregate.AGGREGATE_ID_METADATA_KEY, id);
            metadata.put(Aggregate.AGGREGATE_TYPE_METADATA_KEY, aggregateType.getName());
            
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    serializer.serialize(eventMessage), eventMessage::getPayload), EVENT);
            model = model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                    .lastEventId(eventMessage.getMessageId()).timestamp(eventMessage.getTimestamp())
                    .model(eventSourcingHandler.invoke(model.get(), deserializingMessage)).build();
            unpublishedEvents.add(deserializingMessage);
            return this;
        }

        @Override
        public T get() {
            return model.get();
        }

        @Override
        public String lastEventId() {
            return model.lastEventId();
        }

        @Override
        public Instant timestamp() {
            return model.timestamp();
        }

        protected void commit() {
            if (!unpublishedEvents.isEmpty()) {
                try {
                    cache.put(keyFunction.apply(model.id()), model);
                    eventStore.storeDomainEvents(model.id(), domain, model.sequenceNumber(),
                                                 new ArrayList<>(unpublishedEvents));
                    if (snapshotTrigger.shouldCreateSnapshot(model, unpublishedEvents)) {
                        snapshotRepository.storeSnapshot(model);
                    }
                } catch (Exception e) {
                    log.error("Failed to commit new events of aggregate {}", model.id(), e);
                    cache.invalidate(keyFunction.apply(model.id()));
                } finally {
                    unpublishedEvents.clear();
                }
            }
        }
    }
}
