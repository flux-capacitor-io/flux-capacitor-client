package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Data;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.common.handling.ParameterResolver;
import io.fluxcapacitor.common.serialization.Revision;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.caching.NoCache;
import io.fluxcapacitor.javaclient.common.model.Model;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
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

    private final Map<Class<?>, Function<LoadSettings, EventSourcedModel<?>>> modelFactories =
            new ConcurrentHashMap<>();
    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final List<ParameterResolver<? super DeserializingMessage>> parameterResolvers;
    private final ThreadLocal<Collection<EventSourcedModel<?>>> loadedModels = new ThreadLocal<>();

    public DefaultEventSourcing(EventStore eventStore, SnapshotRepository snapshotRepository, Cache cache) {
        this(eventStore, snapshotRepository, cache, defaultParameterResolvers);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Model<T> load(String modelId, Class<T> modelType, boolean disableCaching, boolean disableSnapshotting) {
        Collection<EventSourcedModel<?>> loaded = loadedModels.get();
        if (loaded == null) {
            return createEsModel(modelType, modelId, disableCaching, disableSnapshotting);
        }
        return loaded.stream().filter(model -> model.id.equals(modelId)).map(m -> (EventSourcedModel<T>) m).findAny()
                .orElseGet(() -> {
                    EventSourcedModel<T> model = createEsModel(modelType, modelId, disableCaching, disableSnapshotting);
                    loaded.add(model);
                    return model;
                });
    }

    @Override
    public void invalidateCache() {
        cache.invalidateAll();
    }

    @Override
    public EventStore eventStore() {
        return eventStore;
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcedModel<T> createEsModel(Class<T> modelType, String modelId, boolean disableCaching,
                                                     boolean disableSnapshotting) {
        return (EventSourcedModel<T>) modelFactories.computeIfAbsent(modelType, t -> {
            EventSourcingHandler<T> eventSourcingHandler =
                    new AnnotatedEventSourcingHandler<>(modelType, parameterResolvers);
            Cache cache = cache(modelType);
            SnapshotRepository snapshotRepository = snapshotRepository(modelType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(modelType);
            String domain = domain(modelType);
            return settings -> {
                EventSourcedModel<T> eventSourcedModel = new EventSourcedModel<>(eventSourcingHandler,
                                                                                 settings.disableCaching ?
                                                                                         NoCache.INSTANCE : cache,
                                                                                 eventStore,
                                                                                 settings.disableSnapshotting ?
                                                                                         NoOpSnapshotRepository.INSTANCE :
                                                                                         snapshotRepository,
                                                                                 snapshotTrigger, domain,
                                                                                 loadedModels.get() == null,
                                                                                 settings.modelId);
                eventSourcedModel.initialize();
                return eventSourcedModel;
            };
        }).apply(new LoadSettings(modelId, disableCaching, disableSnapshotting));
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler,
                                                                    String consumer) {
        return command -> {
            List<EventSourcedModel<?>> models = new ArrayList<>();
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
    protected SnapshotRepository snapshotRepository(Class<?> modelType) {
        int frequency =
                ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::snapshotPeriod)
                        .orElse((int) EventSourced.class.getMethod("snapshotPeriod").getDefaultValue());
        return frequency > 0 ? this.snapshotRepository : NoOpSnapshotRepository.INSTANCE;
    }

    @SneakyThrows
    protected SnapshotTrigger snapshotTrigger(Class<?> modelType) {
        int frequency =
                ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::snapshotPeriod)
                        .orElse((int) EventSourced.class.getMethod("snapshotPeriod").getDefaultValue());
        return frequency > 0 ? new PeriodicSnapshotTrigger(frequency) : NoSnapshotTrigger.INSTANCE;
    }

    @SneakyThrows
    protected Cache cache(Class<?> modelType) {
        boolean cached =
                ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::cached)
                        .orElse((boolean) EventSourced.class.getMethod("cached").getDefaultValue());
        return cached ? this.cache : NoCache.INSTANCE;
    }

    protected String domain(Class<?> modelType) {
        return ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::domain)
                .filter(s -> !s.isEmpty()).orElse(modelType.getSimpleName());
    }

    @AllArgsConstructor
    protected static class LoadSettings {
        String modelId;
        boolean disableCaching;
        boolean disableSnapshotting;
    }

    @RequiredArgsConstructor
    protected static class EventSourcedModel<T> implements Model<T> {

        private final EventSourcingHandler<T> eventSourcingHandler;
        private final Cache cache;
        private final EventStore eventStore;
        private final SnapshotRepository snapshotRepository;
        private final SnapshotTrigger snapshotTrigger;
        private final String domain;
        private final List<Message> unpublishedEvents = new ArrayList<>();
        private final boolean readOnly;
        private final String id;
        private Aggregate<T> aggregate;

        protected void initialize() {
            aggregate = Optional.<Aggregate<T>>ofNullable(cache.getIfPresent(id)).orElseGet(() -> {
                Aggregate<T> aggregate = snapshotRepository.<T>getSnapshot(id)
                        .orElse(new Aggregate<>(id, -1L, null));
                for (DeserializingMessage event : eventStore.getDomainEvents(id, aggregate.getSequenceNumber())
                        .collect(toList())) {
                    aggregate = aggregate.update(m -> eventSourcingHandler.apply(event, m));
                }
                return aggregate;
            });
        }

        @Override
        public Model<T> apply(Message message) {
            if (readOnly) {
                throw new EventSourcingException(format("Not allowed to apply a %s. The model is readonly.", message));
            }
            unpublishedEvents.add(message);
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    new SerializedMessage(new Data<>(() -> {
                        throw new UnsupportedOperationException("Serialized data not available");
                    }, message.getPayload().getClass().getName(), ofNullable(
                            message.getPayload().getClass().getAnnotation(Revision.class)).map(Revision::value)
                            .orElse(0)), message.getMetadata(), message.getMessageId(),
                                          message.getTimestamp().toEpochMilli()),
                    message::getPayload), EVENT, true);
            aggregate = aggregate.update(a -> eventSourcingHandler.apply(deserializingMessage, a));
            return this;
        }

        @Override
        public T get() {
            return aggregate.getModel();
        }

        @Override
        public long getSequenceNumber() {
            return aggregate.getSequenceNumber();
        }

        protected void commit() {
            if (!unpublishedEvents.isEmpty()) {
                cache.put(aggregate.getId(), aggregate);
                eventStore.storeDomainEvents(aggregate.getId(), domain, aggregate.getSequenceNumber(),
                                             new ArrayList<>(unpublishedEvents));
                if (snapshotTrigger.shouldCreateSnapshot(aggregate, unpublishedEvents)) {
                    snapshotRepository.storeSnapshot(aggregate);
                }
                unpublishedEvents.clear();
            }
        }
    }
}
