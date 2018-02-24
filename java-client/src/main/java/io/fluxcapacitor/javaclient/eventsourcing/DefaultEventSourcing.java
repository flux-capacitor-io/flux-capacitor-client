package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.caching.NoCache;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@Slf4j
@AllArgsConstructor
public class DefaultEventSourcing implements EventSourcing, HandlerInterceptor {

    private final Map<Class, Function<String, DefaultEsModel<?>>> modelFactories = new ConcurrentHashMap<>();
    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final ThreadLocal<List<DefaultEsModel<?>>> loadedModels = new ThreadLocal<>();

    @Override
    public <T> EsModel<T> newInstance(String id, Class<T> modelType) {
        EsModel<T> result = doLoad(id, modelType);
        if (result.get() != null) {
            throw new DuplicateModelException(
                    format("Model of type %s id %s already exists", modelType.getSimpleName(), id));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> EsModel<T> load(String modelId, Class<T> modelType) {
        EsModel<T> model = doLoad(modelId, modelType);
        if (model.get() == null) {
            throw new ModelNotFoundException(
                    format("No events founds for type %s id %s", modelType.getSimpleName(), modelId));
        }
        return model;
    }

    @SuppressWarnings("unchecked")
    protected <T> EsModel<T> doLoad(String modelId, Class<T> modelType) {
        DefaultEsModel<T> model = createEsModel(modelType, modelId);
        Optional.ofNullable(loadedModels.get()).ifPresent(models -> models.add(model));
        return model;
    }

    @Override
    public <T> EventSourcingRepository<T> repository(Class<T> modelClass) {
        return new DefaultEventSourcingRepository<>(this, modelClass);
    }

    @Override
    public EventStore eventStore() {
        return eventStore;
    }

    @SuppressWarnings("unchecked")
    protected <T> DefaultEsModel<T> createEsModel(Class<T> modelType, String modelId) {
        return (DefaultEsModel<T>) modelFactories.computeIfAbsent(modelType, t -> {
            EventSourcingHandler<T> eventSourcingHandler = new AnnotatedEventSourcingHandler<>(modelType);
            Cache cache = cache(modelType);
            SnapshotRepository snapshotRepository = snapshotRepository(modelType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(modelType);
            String domain = domain(modelType);
            return id -> new DefaultEsModel<>(eventSourcingHandler, cache, eventStore, snapshotRepository,
                                              snapshotTrigger, id, domain, loadedModels.get() == null);
        }).apply(modelId);
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function) {
        return command -> {
            List<DefaultEsModel<?>> models = new ArrayList<>();
            loadedModels.set(models);
            try {
                Object result = function.apply(command);
                try {
                    loadedModels.get().forEach(DefaultEsModel::commit);
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
                Optional.ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::snapshotPeriod)
                        .orElse((int) EventSourced.class.getMethod("snapshotPeriod").getDefaultValue());
        return frequency > 0 ? this.snapshotRepository : NoOpSnapshotRepository.INSTANCE;
    }

    @SneakyThrows
    protected SnapshotTrigger snapshotTrigger(Class<?> modelType) {
        int frequency =
                Optional.ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::snapshotPeriod)
                        .orElse((int) EventSourced.class.getMethod("snapshotPeriod").getDefaultValue());
        return frequency > 0 ? new PeriodicSnapshotTrigger(frequency) : NoSnapshotTrigger.INSTANCE;
    }

    @SneakyThrows
    protected Cache cache(Class<?> modelType) {
        boolean cached =
                Optional.ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::cached)
                        .orElse((boolean) EventSourced.class.getMethod("cached").getDefaultValue());
        return cached ? this.cache : NoCache.INSTANCE;
    }

    protected String domain(Class<?> modelType) {
        return Optional.ofNullable(modelType.getAnnotation(EventSourced.class)).map(EventSourced::domain)
                .filter(s -> !s.isEmpty()).orElse(modelType.getSimpleName());
    }

    protected static class DefaultEsModel<T> implements EsModel<T> {

        private final EventSourcingHandler<T> eventSourcingHandler;
        private final Cache cache;
        private final EventStore eventStore;
        private final SnapshotRepository snapshotRepository;
        private final SnapshotTrigger snapshotTrigger;
        private final String domain;
        private Aggregate<T> aggregate;
        private final List<Message> unpublishedEvents = new ArrayList<>();
        private final boolean readOnly;

        protected DefaultEsModel(EventSourcingHandler<T> eventSourcingHandler, Cache cache, EventStore eventStore,
                                 SnapshotRepository snapshotRepository, SnapshotTrigger snapshotTrigger,
                                 String id, String domain, boolean readOnly) {
            this.eventSourcingHandler = eventSourcingHandler;
            this.cache = cache;
            this.eventStore = eventStore;
            this.snapshotRepository = snapshotRepository;
            this.snapshotTrigger = snapshotTrigger;
            this.domain = domain;
            this.readOnly = readOnly;
            initializeModel(id);
        }

        protected void initializeModel(String id) {
            aggregate = cache.get(id, i -> {
                Aggregate<T> aggregate = snapshotRepository.<T>getSnapshot(id).orElse(new Aggregate<>(id, -1L, null));
                for (DeserializingMessage event : eventStore.getDomainEvents(id, aggregate.getSequenceNumber())
                        .collect(toList())) {
                    aggregate = aggregate.update(m -> eventSourcingHandler.apply(event.toMessage(), m));
                }
                return aggregate;
            });
        }

        @Override
        public EsModel<T> apply(Message message) {
            if (readOnly) {
                throw new EventSourcingException(format("Not allowed to apply a %s. The model is readonly.", message));
            }
            unpublishedEvents.add(message);
            aggregate = aggregate.update(m -> eventSourcingHandler.apply(message, m));
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
                eventStore.storeDomainEvents(aggregate.getId(), domain, aggregate.getSequenceNumber(),
                                             new ArrayList<>(unpublishedEvents));
                cache.put(aggregate.getId(), aggregate);
                if (snapshotTrigger.shouldCreateSnapshot(aggregate, unpublishedEvents)) {
                    snapshotRepository.storeSnapshot(aggregate);
                }
                unpublishedEvents.clear();
            }
        }
    }
}
