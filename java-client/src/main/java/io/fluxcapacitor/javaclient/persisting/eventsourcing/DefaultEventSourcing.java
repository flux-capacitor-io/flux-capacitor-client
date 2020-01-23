package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

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
public class DefaultEventSourcing implements EventSourcing, HandlerInterceptor {

    private final Map<Class<?>, Function<LoadSettings, EventSourcedAggregate<?>>> modelFactories =
            new ConcurrentHashMap<>();
    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final ThreadLocal<Collection<EventSourcedAggregate<?>>> loadedModels = new ThreadLocal<>();

    public DefaultEventSourcing(EventStore eventStore, SnapshotRepository snapshotRepository, Cache cache) {
        this(eventStore, snapshotRepository, cache, defaultParameterResolvers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean disableCaching,
                                 boolean disableSnapshotting) {
        Collection<EventSourcedAggregate<?>> loaded = loadedModels.get();
        if (loaded == null) {
            return createAggregate(aggregateType, aggregateId, disableCaching, disableSnapshotting);
        }
        return loaded.stream().filter(model -> model.id.equals(aggregateId)).map(m -> (EventSourcedAggregate<T>) m)
                .findAny()
                .orElseGet(() -> {
                    EventSourcedAggregate<T>
                            model = createAggregate(aggregateType, aggregateId, disableCaching, disableSnapshotting);
                    loaded.add(model);
                    return model;
                });
    }

    @Override
    public EventStore eventStore() {
        return eventStore;
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcedAggregate<T> createAggregate(Class<T> aggregateType, String aggregateId,
                                                           boolean disableCaching,
                                                           boolean disableSnapshotting) {
        return (EventSourcedAggregate<T>) modelFactories.computeIfAbsent(aggregateType, t -> {
            EventSourcingHandler<T> eventSourcingHandler =
                    new AnnotatedEventSourcingHandler<>(aggregateType, parameterResolvers);
            Cache cache = cache(aggregateType);
            SnapshotRepository snapshotRepository = snapshotRepository(aggregateType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(aggregateType);
            String domain = domain(aggregateType);
            return settings -> {
                EventSourcedAggregate<T> eventSourcedAggregate = new EventSourcedAggregate<>(
                        eventSourcingHandler, settings.disableCaching ? NoOpCache.INSTANCE : cache, eventStore,
                        settings.disableSnapshotting ? NoOpSnapshotRepository.INSTANCE : snapshotRepository,
                        snapshotTrigger, domain, loadedModels.get() == null, settings.aggregateId);
                eventSourcedAggregate.initialize();
                return eventSourcedAggregate;
            };
        }).apply(new LoadSettings(aggregateId, disableCaching, disableSnapshotting));
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
    protected Cache cache(Class<?> aggregateType) {
        boolean cached =
                ofNullable(aggregateType.getAnnotation(EventSourced.class)).map(EventSourced::cached)
                        .orElse((boolean) EventSourced.class.getMethod("cached").getDefaultValue());
        return cached ? this.cache : NoOpCache.INSTANCE;
    }

    protected String domain(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(EventSourced.class)).map(EventSourced::domain)
                .filter(s -> !s.isEmpty()).orElse(aggregateType.getSimpleName());
    }

    @AllArgsConstructor
    protected static class LoadSettings {
        String aggregateId;
        boolean disableCaching;
        boolean disableSnapshotting;
    }

    @RequiredArgsConstructor
    protected static class EventSourcedAggregate<T> implements Aggregate<T> {

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
            model = Optional.<EventSourcedModel<T>>ofNullable(cache.getIfPresent(id)).orElseGet(() -> {
                EventSourcedModel<T> model = snapshotRepository.<T>getSnapshot(id)
                        .orElse(new EventSourcedModel<>(id, -1L, null));
                for (DeserializingMessage event : eventStore.getDomainEvents(id, model.getSequenceNumber())
                        .collect(toList())) {
                    model = model.update(m -> eventSourcingHandler.apply(event, m));
                }
                return model;
            });
        }

        @Override
        public Aggregate<T> apply(Message message) {
            if (readOnly) {
                throw new EventSourcingException(format("Not allowed to apply a %s. The model is readonly.", message));
            }
            Metadata metadata = message.getMetadata();
            metadata.put(Aggregate.AGGREGATE_ID_METADATA_KEY, id);
            unpublishedEvents.add(message);
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    new SerializedMessage(new Data<>(() -> {
                        throw new UnsupportedOperationException("Serialized data not available");
                    }, message.getPayload().getClass().getName(), ofNullable(
                            message.getPayload().getClass().getAnnotation(Revision.class)).map(Revision::value)
                                                             .orElse(0)), metadata, message.getMessageId(),
                                          message.getTimestamp().toEpochMilli()),
                    message::getPayload), EVENT);
            model = model.update(a -> eventSourcingHandler.apply(deserializingMessage, a));
            return this;
        }

        @Override
        public T get() {
            return model.getModel();
        }

        @Override
        public long getSequenceNumber() {
            return model.getSequenceNumber();
        }

        protected void commit() {
            if (!unpublishedEvents.isEmpty()) {
                cache.put(model.getId(), model);
                eventStore.storeDomainEvents(model.getId(), domain, model.getSequenceNumber(),
                                             new ArrayList<>(unpublishedEvents));
                if (snapshotTrigger.shouldCreateSnapshot(model, unpublishedEvents)) {
                    snapshotRepository.storeSnapshot(model);
                }
                unpublishedEvents.clear();
            }
        }
    }
}
