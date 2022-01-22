/*
 * Copyright (c) 2016-2021 Flux Capacitor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.persisting.search.DocumentStore;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.common.Message.asMessage;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.defaultParameterResolvers;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.whenBatchCompletes;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.whenMessageCompletes;
import static java.lang.String.format;
import static java.util.Collections.asLifoQueue;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

@Slf4j
@AllArgsConstructor
public class DefaultAggregateRepository implements AggregateRepository {
    private static final Function<String, String> keyFunction = aggregateId ->
            DefaultAggregateRepository.class.getSimpleName() + ":" + aggregateId;

    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final DocumentStore documentStore;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    private final EventSourcingHandlerFactory handlerFactory;

    private final Map<Class<?>, AggregateFactoryFunction> aggregateFactory = new ConcurrentHashMap<>();
    private final ThreadLocal<Collection<EventSourcedAggregate<?>>> loadedModels = new ThreadLocal<>();

    public DefaultAggregateRepository(EventStore eventStore, SnapshotRepository snapshotRepository, Cache cache,
                                      DocumentStore documentStore, Serializer serializer,
                                      DispatchInterceptor dispatchInterceptor) {
        this(eventStore, snapshotRepository, cache, documentStore, serializer, dispatchInterceptor,
             new DefaultEventSourcingHandlerFactory(defaultParameterResolvers));
    }

    @Override
    public boolean supports(Class<?> aggregateType) {
        return aggregateType.isAnnotationPresent(Aggregate.class);
    }

    @Override
    public boolean cachingAllowed(Class<?> aggregateType) {
        Aggregate aggregate = aggregateType.getAnnotation(Aggregate.class);
        if (aggregate == null) {
            throw new UnsupportedOperationException("Unsupported aggregate type: " + aggregateType);
        }
        return aggregate.cached();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> AggregateRoot<T> load(String aggregateId, Class<T> aggregateType, boolean readOnly, boolean onlyCached) {
        Optional<EventSourcedAggregate<T>> loadedModel = ofNullable(loadedModels.get()).orElse(emptyList()).stream()
                .filter(model -> model.id.equals(aggregateId)
                        && aggregateType.isAssignableFrom(model.getAggregateType()))
                .map(m -> {
                    EventSourcedAggregate<T> result = (EventSourcedAggregate<T>) m;
                    if (readOnly) {
                        return result.withReadOnly(true);
                    }
                    return result;
                })
                .findAny();
        if (onlyCached) {
            return loadedModel
                    .or(() -> Optional.<EventSourcedModel<T>>ofNullable(
                                    cache.getIfPresent(keyFunction.apply(aggregateId)))
                            .map(m -> {
                                EventSourcedAggregate<T> aggregate =
                                        createAggregate(aggregateType, aggregateId, readOnly, false);
                                aggregate.model = m;
                                return aggregate;
                            })).orElse(null);
        }
        return loadedModel.orElseGet(() -> createAggregate(aggregateType, aggregateId, readOnly, true));
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcedAggregate<T> createAggregate(Class<T> aggregateType, String aggregateId, boolean readOnly,
                                                           boolean initialize) {
        return (EventSourcedAggregate<T>) aggregateFactory.computeIfAbsent(aggregateType, t -> {
            EventSourcingHandler<T> eventSourcingHandler = handlerFactory.forType(aggregateType);
            Cache cache = isCached(aggregateType) ? this.cache : NoOpCache.INSTANCE;
            SnapshotRepository snapshotRepository = snapshotRepository(aggregateType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(aggregateType);
            boolean eventSourced = eventSourced(aggregateType);
            boolean searchable = searchable(aggregateType);
            String collection = collection(aggregateType);
            Function<AggregateRoot<?>, Instant> timestampFunction = timestampFunction(aggregateType);
            return (id, ro, init) -> {
                EventSourcedAggregate<T> eventSourcedAggregate = new EventSourcedAggregate<>(
                        aggregateType, eventSourcingHandler, cache, serializer, dispatchInterceptor,
                        eventStore, snapshotRepository, snapshotTrigger, documentStore, eventSourced, searchable,
                        collection, timestampFunction, ro, id);
                if (init) {
                    eventSourcedAggregate.initialize();
                }
                return eventSourcedAggregate;
            };
        }).create(aggregateId, readOnly, initialize);
    }

    @SneakyThrows
    protected SnapshotRepository snapshotRepository(Class<?> aggregateType) {
        int frequency = snapshotPeriod(aggregateType);
        return frequency > 0 ? this.snapshotRepository : NoOpSnapshotRepository.INSTANCE;
    }

    @SneakyThrows
    protected SnapshotTrigger snapshotTrigger(Class<?> aggregateType) {
        int frequency = snapshotPeriod(aggregateType);
        return frequency > 0 ? new PeriodicSnapshotTrigger(frequency) : NoSnapshotTrigger.INSTANCE;
    }

    @SneakyThrows
    protected int snapshotPeriod(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class))
                .map(a -> a.eventSourced() || a.searchable() ? a.snapshotPeriod() : 1)
                .orElse((int) Aggregate.class.getMethod("snapshotPeriod").getDefaultValue());
    }

    @SneakyThrows
    protected boolean isCached(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::cached)
                .orElse((boolean) Aggregate.class.getMethod("cached").getDefaultValue());
    }

    protected String collection(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::collection)
                .filter(s -> !s.isEmpty()).orElse(aggregateType.getSimpleName());
    }

    @SneakyThrows
    protected boolean eventSourced(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::eventSourced)
                .orElse((boolean) Aggregate.class.getMethod("eventSourced").getDefaultValue());
    }

    @SneakyThrows
    protected boolean searchable(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::searchable)
                .orElse((boolean) Aggregate.class.getMethod("searchable").getDefaultValue());
    }

    @SneakyThrows
    protected Function<AggregateRoot<?>, Instant> timestampFunction(Class<?> aggregateType) {
        AtomicBoolean warnedAboutMissingProperty = new AtomicBoolean();
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::timestampPath)
                .filter(s -> !s.isBlank()).<Function<AggregateRoot<?>, Instant>>map(
                        s -> aggregateRoot -> ReflectionUtils.readProperty(s, aggregateRoot.get())
                                .map(t -> Instant.from((TemporalAccessor) t)).orElseGet(() -> {
                                    if (warnedAboutMissingProperty.compareAndSet(false, true)) {
                                        log.warn("Aggregate type {} does not declare the timestamp path property '{}'",
                                                 aggregateRoot.get().getClass().getSimpleName(), s);
                                    }
                                    return aggregateRoot.timestamp();
                                }))
                .orElse(AggregateRoot::timestamp);
    }

    @RequiredArgsConstructor
    @AllArgsConstructor
    protected class EventSourcedAggregate<T> implements AggregateRoot<T> {

        private final Class<T> aggregateType;
        private final EventSourcingHandler<T> eventSourcingHandler;
        private final Cache cache;
        private final Serializer serializer;
        private final DispatchInterceptor dispatchInterceptor;
        private final EventStore eventStore;
        private final SnapshotRepository snapshotRepository;
        private final SnapshotTrigger snapshotTrigger;
        private final DocumentStore documentStore;
        private final boolean eventSourced;
        private final boolean searchable;
        private final String collection;
        private final Function<AggregateRoot<?>, Instant> timestampFunction;
        private final List<DeserializingMessage> unpublishedEvents = new ArrayList<>();
        @With
        private final boolean readOnly;
        private final String id;

        private EventSourcedModel<T> model;
        private boolean updated;

        protected void initialize() {
            model = Optional.<EventSourcedModel<T>>ofNullable(cache.getIfPresent(keyFunction.apply(id)))
                    .filter(a -> a.get() == null || aggregateType.isAssignableFrom(a.get().getClass()))
                    .orElseGet(() -> {
                        EventSourcedModel<T> model =
                                (searchable && !eventSourced ? documentStore.<T>fetchDocument(id, collection)
                                        .map(d -> EventSourcedModel.<T>builder().id(id).type(aggregateType).model(
                                                d).build()) : snapshotRepository.<T>getSnapshot(id))
                                        .filter(a -> {
                                            boolean assignable = a.get() == null
                                                    || aggregateType.isAssignableFrom(a.get().getClass());
                                            if (!assignable) {
                                                log.warn("Could not load aggregate {} because the requested"
                                                                 + " type {} is not assignable to the stored type {}",
                                                         id, aggregateType, a.get().getClass());
                                            }
                                            return assignable;
                                        })
                                        .orElseGet(() -> EventSourcedModel.<T>builder().id(id).type(aggregateType)
                                                .build());
                        if (!eventSourced) {
                            return model;
                        }
                        AggregateEventStream<DeserializingMessage> eventStream
                                = eventStore.getEvents(id, model.sequenceNumber());
                        Iterator<DeserializingMessage> iterator = eventStream.iterator();
                        while (iterator.hasNext()) {
                            DeserializingMessage event = iterator.next();
                            model = model.toBuilder()
                                    .sequenceNumber(model.sequenceNumber() + 1)
                                    .type(aggregateType)
                                    .id(id)
                                    .lastEventId(event.getSerializedObject().getMessageId())
                                    .lastEventIndex(event.getSerializedObject().getIndex())
                                    .timestamp(Instant.ofEpochMilli(event.getSerializedObject().getTimestamp()))
                                    .model(eventSourcingHandler.invoke(model, event))
                                    .previous(model)
                                    .build();
                        }
                        return model.toBuilder()
                                .sequenceNumber(eventStream.getLastSequenceNumber().orElse(model.sequenceNumber()))
                                .build();
                    });
        }

        @SuppressWarnings("rawtypes")
        public Class<?> getAggregateType() {
            return Optional.ofNullable(model).map(EventSourcedModel::get)
                    .map(m -> (Class) m.getClass()).orElse(aggregateType);
        }

        @Override
        public AggregateRoot<T> apply(Message message) {
            if (readOnly) {
                throw new UnsupportedOperationException(format("Not allowed to apply a %s. The model is readonly.",
                                                               message));
            }

            Message m = dispatchInterceptor.interceptDispatch(message.withMetadata(
                    message.getMetadata().with(AggregateRoot.AGGREGATE_ID_METADATA_KEY, id,
                                               AggregateRoot.AGGREGATE_TYPE_METADATA_KEY,
                                               getAggregateType().getName())), EVENT);
            DeserializingMessage deserializingMessage = new DeserializingMessage(
                    dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT),
                    type -> m.getPayload(), EVENT);

            unpublishedEvents.add(deserializingMessage);
            updateModel(model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                                .model(eventSourcingHandler.invoke(model, deserializingMessage))
                                .previous(model).lastEventId(m.getMessageId())
                                .timestamp(m.getTimestamp())
                                .lastEventIndex(deserializingMessage.getSerializedObject().getIndex())
                                .build());
            return this;
        }

        @Override
        public AggregateRoot<T> update(UnaryOperator<T> function) {
            if (eventSourced) {
                log.warn("An event sourced aggregate is updated without applying an event. This is typically a mistake."
                                 + " On aggregate: {}", this);
            }
            updateModel(model.update(function));
            return this;
        }

        protected void updateModel(EventSourcedModel<T> model) {
            this.model = model;
            updated = true;
            if (loadedModels.get() == null) {
                loadedModels.set(asLifoQueue(new ArrayDeque<>()));
                loadedModels.get().add(this);

                Consumer<Throwable> commit = error -> {
                    Collection<EventSourcedAggregate<?>> models = loadedModels.get();
                    loadedModels.remove();
                    models.stream().map(EventSourcedAggregate::commit).reduce(Awaitable::join).ifPresent(a -> {
                        try {
                            a.await();
                        } catch (Exception e) {
                            List<String> aggregateIds = models.stream().map(m -> m.id).collect(toList());
                            log.error("Failed to commit events for aggregates {}. Clearing aggregates from the cache.",
                                      aggregateIds, e);
                            aggregateIds.forEach(id -> cache.invalidate(keyFunction.apply(id)));
                        }
                    });
                };
                if (aggregateType.getAnnotation(Aggregate.class).commitInBatch()) {
                    whenBatchCompletes(commit);
                } else {
                    whenMessageCompletes(commit);
                }
            } else if (loadedModels.get().stream().noneMatch(e -> e == this)) {
                loadedModels.get().add(this);
            }
        }

        @Override
        public <E extends Exception> AggregateRoot<T> assertLegal(Object... commands) throws E {
            switch (commands.length) {
                case 0:
                    return this;
                case 1:
                    ValidationUtils.assertLegal(commands[0], model);
                    return this;
                default:
                    EventSourcedModel<T> result = model;
                    Iterator<Object> iterator = Arrays.stream(commands).iterator();
                    while (iterator.hasNext()) {
                        Object c = iterator.next();
                        ValidationUtils.assertLegal(c, result);
                        if (iterator.hasNext()) {
                            result = forceApply(model, asMessage(c));
                        }
                    }
                    return this;
            }
        }

        protected EventSourcedModel<T> forceApply(EventSourcedModel<T> model, Message message) {
            Message m = message.withMetadata(Metadata.of(AggregateRoot.AGGREGATE_ID_METADATA_KEY, id,
                                                         AggregateRoot.AGGREGATE_TYPE_METADATA_KEY,
                                                         getAggregateType().getName()));
            DeserializingMessage deserializingMessage = new DeserializingMessage(
                    m.serialize(serializer), type -> m.getPayload(), EVENT);
            return model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                    .model(eventSourcingHandler.invoke(model, deserializingMessage))
                    .previous(model).lastEventId(m.getMessageId()).timestamp(m.getTimestamp())
                    .lastEventIndex(deserializingMessage.getSerializedObject().getIndex())
                    .build();
        }

        @Override
        public T get() {
            return model.get();
        }

        @Override
        public AggregateRoot<T> previous() {
            return model.previous();
        }

        @Override
        public String lastEventId() {
            return model.lastEventId();
        }

        @Override
        public Long lastEventIndex() {
            return model.lastEventIndex();
        }


        @Override
        public Instant timestamp() {
            return model.timestamp();
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Class<T> type() {
            return aggregateType;
        }

        @Override
        public String toString() {
            return "EventSourcedAggregate{" +
                    "collection='" + collection + '\'' +
                    ", eventSourced=" + eventSourced +
                    ", searchable=" + searchable +
                    ", readOnly=" + readOnly +
                    ", model=" + model +
                    '}';
        }

        protected Awaitable commit() {
            Awaitable result = Awaitable.ready();
            if (updated) {
                try {
                    cache.put(keyFunction.apply(model.id()), model);
                    if (!unpublishedEvents.isEmpty()) {
                        result = eventStore.storeEvents(model.id(), collection, model.sequenceNumber(),
                                                        new ArrayList<>(unpublishedEvents));
                        if (snapshotTrigger.shouldCreateSnapshot(model, unpublishedEvents)) {
                            snapshotRepository.storeSnapshot(model);
                        }
                    }
                    if (searchable) {
                        T value = model.get();
                        if (value == null) {
                            documentStore.deleteDocument(model.id(), collection);
                        } else {
                            documentStore.index(value, model.id(), collection, timestampFunction.apply(model));
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to commit new events of aggregate {}", model.id(), e);
                    cache.invalidate(keyFunction.apply(model.id()));
                } finally {
                    unpublishedEvents.clear();
                    updated = false;
                }
            }
            return result;
        }
    }

    @FunctionalInterface
    protected interface AggregateFactoryFunction {
        EventSourcedAggregate<?> create(String aggregateId, boolean readOnly, boolean initialize);
    }
}
