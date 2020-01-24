package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultParameterResolvers;
import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Slf4j
@AllArgsConstructor
public class EventSourcingRepository implements AggregateRepository, HandlerInterceptor {
    private static final Function<String, String> keyFunction = aggregateId ->
            EventSourcingRepository.class.getSimpleName() + ":" + aggregateId;

    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final EventSourcingHandlerFactory handlerFactory;

    private final Map<Class<?>, Function<String, EventSourcedAggregate<?>>> aggregateFactory =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Collection<EventSourcedAggregate<?>>> loadedModels = new ThreadLocal<>();

    public EventSourcingRepository(EventStore eventStore, SnapshotRepository snapshotRepository, Cache cache) {
        this(eventStore, snapshotRepository, cache, new DefaultEventSourcingHandlerFactory(defaultParameterResolvers));
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
        Collection<EventSourcedAggregate<?>> loaded = loadedModels.get();
        if (loaded == null) {
            return createAggregate(aggregateType, aggregateId);
        }
        return loaded.stream().filter(model -> model.id.equals(aggregateId)).map(m -> (EventSourcedAggregate<T>) m)
                .findAny()
                .orElseGet(() -> {
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
                        aggregateType, eventSourcingHandler, cache, eventStore, snapshotRepository,
                        snapshotTrigger, domain, loadedModels.get() == null, id);
                eventSourcedAggregate.initialize();
                return eventSourcedAggregate;
            };
        }).apply(aggregateId);
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return command -> {
            List<EventSourcedAggregate<?>> models = new ArrayList<>();
            loadedModels.set(models);
            try {
                Object result = function.apply(command);
                try {
                    while (!models.isEmpty()) {
                        models.remove(models.size() - 1).commit();
                    }
                } catch (Exception e) {
                    throw new EventSourcingException(
                            format("Failed to commit applied events after handling %s", command), e);
                }
                return result;
            } finally {
                loadedModels.remove();
            }
        };
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
        private final EventStore eventStore;
        private final SnapshotRepository snapshotRepository;
        private final SnapshotTrigger snapshotTrigger;
        private final String domain;
        private final List<Message> unpublishedEvents = new ArrayList<>();
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
            unpublishedEvents.add(eventMessage);
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    new SerializedMessage(new Data<>(() -> {
                        throw new UnsupportedOperationException("Serialized data not available");
                    }, eventMessage.getPayload().getClass().getName(), ofNullable(
                            eventMessage.getPayload().getClass().getAnnotation(Revision.class)).map(Revision::value)
                                                             .orElse(0)), metadata, eventMessage.getMessageId(),
                                          eventMessage.getTimestamp().toEpochMilli()),
                    eventMessage::getPayload), EVENT);
            model = model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                    .lastEventId(eventMessage.getMessageId()).timestamp(eventMessage.getTimestamp())
                    .model(eventSourcingHandler.invoke(model.get(), deserializingMessage)).build();
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
                cache.put(keyFunction.apply(model.id()), model);
                eventStore.storeDomainEvents(model.id(), domain, model.sequenceNumber(),
                                             new ArrayList<>(unpublishedEvents));
                if (snapshotTrigger.shouldCreateSnapshot(model, unpublishedEvents)) {
                    snapshotRepository.storeSnapshot(model);
                }
                unpublishedEvents.clear();
            }
        }
    }
}
