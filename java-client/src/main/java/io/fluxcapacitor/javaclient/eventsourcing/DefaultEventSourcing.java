package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerInterceptor;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DefaultEventSourcing implements EventSourcing, HandlerInterceptor {

    private static final Map<Class, EventSourcingHandler> handlerCache = new ConcurrentHashMap<>();

    private final EventStore eventStore;
    private final Cache cache;
    private List<DefaultEsModel<?>> loadedModels;

    @Override
    public <T> EsModel<T> load(String id, Class<T> modelType) {
        DefaultEsModel<T> model = new DefaultEsModel<>(getEventSourcingHandler(modelType), cache, eventStore, id,
                                                       loadedModels == null);
        Optional.ofNullable(loadedModels).ifPresent(models -> models.add(model));
        return model;
    }

    @Override
    public <T> EventSourcingRepository<T> repository(Class<T> modelClass) {
        return (modelId, expectedSequenceNumber) -> {
            EsModel<T> result = load(modelId, modelClass);
            if (expectedSequenceNumber != null && expectedSequenceNumber != result.getSequenceNumber()) {
                throw new EventSourcingException(String.format(
                        "Failed to load %s of id %s. Expected sequence number %d but model had sequence number %d",
                        modelClass.getSimpleName(), modelId, expectedSequenceNumber, result.getSequenceNumber()));
            }
            return result;
        };
    }

    @Override
    public EventStore eventStore() {
        return eventStore;
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcingHandler<T> getEventSourcingHandler(Class<T> modelType) {
        return handlerCache.computeIfAbsent(modelType, AnnotatedEventSourcingHandler::new);
    }

    @Override
    public Function<DeserializingMessage, Object> interceptHandling(Function<DeserializingMessage, Object> function) {
        return command -> {
            loadedModels = new ArrayList<>();
            try {
                Object result = function.apply(command);
                try {
                    loadedModels.forEach(DefaultEsModel::commit);
                } catch (Exception e) {
                    throw new EventSourcingException(
                            String.format("Failed to commit applied events after handling %s", command), e);
                }
                return result;
            } finally {
                loadedModels = null;
            }
        };
    }

    protected static class DefaultEsModel<T> implements EsModel<T> {

        private final EventSourcingHandler<T> eventSourcingHandler;
        private final Cache cache;
        private final EventStore eventStore;
        private Aggregate<T> aggregate;
        private final List<Message> unpublishedEvents = new ArrayList<>();
        private final boolean readOnly;

        protected DefaultEsModel(EventSourcingHandler<T> eventSourcingHandler, Cache cache, EventStore eventStore,
                                 String id, boolean readOnly) {
            this.eventSourcingHandler = eventSourcingHandler;
            this.cache = cache;
            this.eventStore = eventStore;
            this.readOnly = readOnly;
            initializeModel(id);
        }

        protected void initializeModel(String id) {
            aggregate = cache.get(id, i -> {
                Aggregate<T> aggregate = eventStore.<T>getSnapshot(id).orElse(new Aggregate<>(id, -1L, null));
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
                eventStore.storeDomainEvents(aggregate.getId(), aggregate.getSequenceNumber(),
                                             new ArrayList<>(unpublishedEvents));
                cache.put(aggregate.getId(), aggregate);
                unpublishedEvents.clear();
            }
        }
    }
}
