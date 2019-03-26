package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.caching.NoCache;
import io.fluxcapacitor.javaclient.common.model.Model;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
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

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@Slf4j
@AllArgsConstructor
public class DefaultEventSourcing implements EventSourcing, HandlerInterceptor {

    private final Map<Class, Function<String, EventSourcedModel<?>>> modelFactories = new ConcurrentHashMap<>();
    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final ThreadLocal<Collection<EventSourcedModel<?>>> loadedModels = new ThreadLocal<>();

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
            EventSourcingHandler<T> eventSourcingHandler = new AnnotatedEventSourcingHandler<>(modelType);
            Cache cache = disableCaching ? NoCache.INSTANCE : cache(modelType);
            SnapshotRepository snapshotRepository = disableSnapshotting 
                    ? NoOpSnapshotRepository.INSTANCE : snapshotRepository(modelType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(modelType);
            String domain = domain(modelType);
            return id -> {
                EventSourcedModel<T> eventSourcedModel =
                        new EventSourcedModel<>(eventSourcingHandler, cache, eventStore, snapshotRepository,
                                                snapshotTrigger, domain, loadedModels.get() == null, id);
                eventSourcedModel.initialize();
                return eventSourcedModel;
            };
        }).apply(modelId);
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function,
                                                                    Handler<DeserializingMessage> handler, String consumer) {
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
            aggregate = cache.get(id, i -> {
                Aggregate<T> aggregate = snapshotRepository.<T>getSnapshot(id).orElse(new Aggregate<>(id, -1L, null));
                for (DeserializingMessage event : eventStore.getDomainEvents(id, aggregate.getSequenceNumber())
                        .collect(toList())) {
                    aggregate = aggregate.update(m -> {
                        DeserializingMessage current = DeserializingMessage.getCurrent();
                        try {
                            DeserializingMessage.setCurrent(event);
                            return eventSourcingHandler.apply(event.toMessage(), m);
                        } finally {
                            DeserializingMessage.setCurrent(current);
                        }
                    });
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
