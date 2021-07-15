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
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.persisting.caching.NoOpCache;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
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
public class EventSourcingRepository implements AggregateRepository {
    private static final Function<String, String> keyFunction = aggregateId ->
            EventSourcingRepository.class.getSimpleName() + ":" + aggregateId;

    private final EventStore eventStore;
    private final SnapshotRepository snapshotRepository;
    private final Cache cache;
    private final EventStoreSerializer serializer;
    private final EventSourcingHandlerFactory handlerFactory;

    private final Map<Class<?>, AggregateFactoryFunction> aggregateFactory = new ConcurrentHashMap<>();
    private final ThreadLocal<Collection<EventSourcedAggregate<?>>> loadedModels = new ThreadLocal<>();

    public EventSourcingRepository(EventStore eventStore, SnapshotRepository snapshotRepository, Cache cache,
                                   EventStoreSerializer serializer) {
        this(eventStore, snapshotRepository, cache, serializer,
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
        if (onlyCached) {
            return Optional.<EventSourcedModel<T>>ofNullable(cache.getIfPresent(keyFunction.apply(aggregateId)))
                    .map(m -> {
                        EventSourcedAggregate<T> aggregate =
                                createAggregate(aggregateType, aggregateId, readOnly, false);
                        aggregate.model = m;
                        return aggregate;
                    }).orElse(null);
        }
        return ofNullable(loadedModels.get()).orElse(emptyList()).stream()
                .filter(model -> model.id.equals(aggregateId)
                        && aggregateType.isAssignableFrom(model.getAggregateType()))
                .map(m -> (EventSourcedAggregate<T>) m)
                .findAny().orElseGet(() -> createAggregate(aggregateType, aggregateId, readOnly, true));
    }

    @SuppressWarnings("unchecked")
    protected <T> EventSourcedAggregate<T> createAggregate(Class<T> aggregateType, String aggregateId, boolean readOnly,
                                                           boolean initialize) {
        return (EventSourcedAggregate<T>) aggregateFactory.computeIfAbsent(aggregateType, t -> {
            EventSourcingHandler<T> eventSourcingHandler = handlerFactory.forType(aggregateType);
            Cache cache = isCached(aggregateType) ? this.cache : NoOpCache.INSTANCE;
            SnapshotRepository snapshotRepository = snapshotRepository(aggregateType);
            SnapshotTrigger snapshotTrigger = snapshotTrigger(aggregateType);
            String domain = domain(aggregateType);
            boolean eventSourced = eventSourced(aggregateType);
            return (id, ro, init) -> {
                EventSourcedAggregate<T> eventSourcedAggregate = new EventSourcedAggregate<>(
                        aggregateType, eventSourcingHandler, cache, serializer, eventStore, snapshotRepository,
                        snapshotTrigger, domain, eventSourced, ro, id);
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
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(a -> a.eventSourced() ? a.snapshotPeriod() : 1)
                .orElse((int) Aggregate.class.getMethod("snapshotPeriod").getDefaultValue());
    }

    @SneakyThrows
    protected boolean isCached(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::cached)
                .orElse((boolean) Aggregate.class.getMethod("cached").getDefaultValue());
    }

    protected String domain(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::domain)
                .filter(s -> !s.isEmpty()).orElse(aggregateType.getSimpleName());
    }

    @SneakyThrows
    protected boolean eventSourced(Class<?> aggregateType) {
        return ofNullable(aggregateType.getAnnotation(Aggregate.class)).map(Aggregate::eventSourced)
                .orElse((boolean) Aggregate.class.getMethod("eventSourced").getDefaultValue());
    }

    @RequiredArgsConstructor
    protected class EventSourcedAggregate<T> implements AggregateRoot<T> {

        private final Class<T> aggregateType;
        private final EventSourcingHandler<T> eventSourcingHandler;
        private final Cache cache;
        private final EventStoreSerializer serializer;
        private final EventStore eventStore;
        private final SnapshotRepository snapshotRepository;
        private final SnapshotTrigger snapshotTrigger;
        private final String domain;
        private final boolean eventSourced;
        private final List<DeserializingMessage> unpublishedEvents = new ArrayList<>();
        private final boolean readOnly;
        private final String id;

        private EventSourcedModel<T> model;

        protected void initialize() {
            model = Optional.<EventSourcedModel<T>>ofNullable(cache.getIfPresent(keyFunction.apply(id)))
                    .filter(a -> aggregateType.isAssignableFrom(a.get().getClass()))
                    .orElseGet(() -> {
                        EventSourcedModel<T> model = snapshotRepository.<T>getSnapshot(id)
                                .filter(a -> aggregateType.isAssignableFrom(a.get().getClass()))
                                .orElse(EventSourcedModel.<T>builder().id(id).type(aggregateType).build());
                        if (!eventSourced) {
                            return model.toBuilder().sequenceNumber(model.sequenceNumber()).build();
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
                                    .model(eventSourcingHandler.invoke(model.get(), event))
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

            Metadata metadata = message.getMetadata()
                    .with(AggregateRoot.AGGREGATE_ID_METADATA_KEY, id,
                          AggregateRoot.AGGREGATE_TYPE_METADATA_KEY, getAggregateType().getName());

            Message eventMessage = message.withMetadata(metadata);
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    serializer.serialize(eventMessage), type -> serializer.convert(eventMessage.getPayload(), type)), EVENT);

            model = model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                    .model(eventSourcingHandler.invoke(model.get(), deserializingMessage))
                    .previous(model).lastEventId(eventMessage.getMessageId()).timestamp(eventMessage.getTimestamp())
                    .lastEventIndex(deserializingMessage.getSerializedObject().getIndex())
                    .build();

            unpublishedEvents.add(deserializingMessage);

            if (loadedModels.get() == null) {
                loadedModels.set(asLifoQueue(new ArrayDeque<>()));
                loadedModels.get().add(this);

                Runnable commit = () -> {
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
            return this;
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
                        ValidationUtils.assertLegal(c, result.get());
                        if (iterator.hasNext()) {
                            result = forceApply(model, c instanceof Message ? (Message) c : new Message(c));
                        }
                    }
                    return this;
            }
        }

        protected EventSourcedModel<T> forceApply(EventSourcedModel<T> model, Message message) {
            Message eventMessage = message.withMetadata(
                    message.getMetadata().with(AggregateRoot.AGGREGATE_ID_METADATA_KEY, id,
                                               AggregateRoot.AGGREGATE_TYPE_METADATA_KEY, getAggregateType().getName()));
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    serializer.serialize(message), type -> serializer.convert(eventMessage.getPayload(), type)),
                                                                                 EVENT);
            return model.toBuilder().sequenceNumber(model.sequenceNumber() + 1)
                    .model(eventSourcingHandler.invoke(model.get(), deserializingMessage))
                    .previous(model).lastEventId(eventMessage.getMessageId()).timestamp(eventMessage.getTimestamp())
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
                    "domain='" + domain + '\'' +
                    ", eventSourced=" + eventSourced +
                    ", readOnly=" + readOnly +
                    ", model=" + model +
                    '}';
        }

        protected Awaitable commit() {
            Awaitable result = Awaitable.ready();
            if (!unpublishedEvents.isEmpty()) {
                try {
                    cache.put(keyFunction.apply(model.id()), model);
                    result = eventStore.storeEvents(model.id(), domain, model.sequenceNumber(),
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
            return result;
        }
    }

    @FunctionalInterface
    protected interface AggregateFactoryFunction {
        EventSourcedAggregate<?> create(String aggregateId, boolean readOnly, boolean initialize);
    }
}
