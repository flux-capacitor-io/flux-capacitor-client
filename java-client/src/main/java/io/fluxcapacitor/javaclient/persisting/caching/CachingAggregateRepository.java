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

package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.IndexUtils;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingObject;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.handling.validation.ValidationUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.javaclient.modeling.AggregateIdResolver.getAggregateId;
import static io.fluxcapacitor.javaclient.modeling.AggregateTypeResolver.getAggregateType;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;
import static java.lang.String.format;
import static java.time.Instant.ofEpochMilli;

@RequiredArgsConstructor
@Slf4j
public class CachingAggregateRepository implements AggregateRepository {
    private static final Function<String, String> keyFunction = aggregateId ->
            CachingAggregateRepository.class.getSimpleName() + ":" + aggregateId;
    public static final Duration slowTrackingThreshold = Duration.ofSeconds(5L);

    private final AggregateRepository delegate;
    private final EventSourcingHandlerFactory handlerFactory;
    private final Cache cache;

    private final Client client;
    private final Serializer serializer;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile long lastEventIndex = -1L;

    @Override
    public <T> AggregateRoot<T> load(@NonNull String aggregateId, @NonNull Class<T> aggregateType, boolean readOnly,
                                     boolean onlyCached) {
        if (!delegate.cachingAllowed(aggregateType) || !readOnly) {
            return delegate.load(aggregateId, aggregateType, readOnly, onlyCached);
        }
        AggregateRoot<T> result = delegate.load(aggregateId, aggregateType, true, true);
        if (result == null) {
            return Optional.<AggregateRoot<T>>ofNullable(doLoad(aggregateId, aggregateType, onlyCached))
                    .filter(a -> Optional.ofNullable(a.get()).map(m -> aggregateType.isAssignableFrom(m.getClass()))
                            .orElse(true))
                    .orElseGet(() -> delegate.load(aggregateId, aggregateType, true, onlyCached));
        }
        return result;
    }

    private <T> RefreshingAggregateRoot<T> doLoad(String aggregateId, Class<T> type, boolean onlyCached) {
        if (started.compareAndSet(false, true)) {
            start(this::handleEvents, ConsumerConfiguration.builder().messageType(NOTIFICATION)
                    .lastIndex(lastEventIndex = IndexUtils.indexForCurrentTime())
                    .name(CachingAggregateRepository.class.getSimpleName()).build(), client);
            synchronized (cache) {
                cache.notifyAll();
            }
        }
        DeserializingMessage current = DeserializingMessage.getCurrent();
        if (current != null) {
            switch (current.getMessageType()) {
                case EVENT:
                case NOTIFICATION:
                    Long eventIndex = current.getSerializedObject().getIndex();
                    if (eventIndex != null && lastEventIndex < eventIndex) {
                        synchronized (cache) {
                            Instant start = Instant.now();
                            while (lastEventIndex < eventIndex) {
                                try {
                                    cache.wait(5_000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    log.warn("Failed to load aggregate for event {}", current, e);
                                    return null;
                                }
                            }
                            Duration fetchDuration = Duration.between(start, Instant.now());
                            if (fetchDuration.compareTo(slowTrackingThreshold) > 0) {
                                log.warn(
                                        "It took over {} to load aggregate {} of type {}. This indicates that the tracker in the caching aggregate repo has trouble keeping up.",
                                        fetchDuration, aggregateId, type);
                            }
                        }
                    }
                    break;
            }
        }
        if (onlyCached) {
            return cache.getIfPresent(keyFunction.apply(aggregateId));
        }
        return cache
                .get(keyFunction.apply(aggregateId), cacheKey -> Optional.ofNullable(delegate.load(aggregateId, type))
                        .map(a -> {
                            EventSourcingHandler<T> handler = handlerFactory.forType(type);
                            return new RefreshingAggregateRoot<>(a.get(), handler, serializer,
                                                                 aggregateId, type, a.previous(),
                                                                 a.lastEventId(),
                                                                 a.lastEventIndex(), a.timestamp(),
                                                                 Status.UNVERIFIED);
                        })
                        .orElse(null));
    }

    protected void handleEvents(List<SerializedMessage> messages) {
        try {
            serializer.deserializeMessages(messages.stream(), false, EVENT)
                    .forEach(m -> {
                        String aggregateId = getAggregateId(m);
                        Class<?> aggregateType = getAggregateType(m);
                        if (aggregateId != null && aggregateType != null && delegate.cachingAllowed(aggregateType)) {
                            try {
                                handleEvent(m, aggregateId, aggregateType);
                            } catch (Exception e) {
                                log.error("Failed to handle event for aggregate with id {} of type {}", aggregateId,
                                          aggregateType, e);
                            }
                        }
                    });
        } finally {
            messages.stream().reduce((a, b) -> b).map(SerializedMessage::getIndex).ifPresent(index -> {
                lastEventIndex = index;
                synchronized (cache) {
                    cache.notifyAll();
                }
            });
        }
    }

    protected <T> void handleEvent(DeserializingMessage event, String aggregateId, Class<T> type) {
        EventSourcingHandler<T> handler = handlerFactory.forType(type);
        String cacheKey = keyFunction.apply(aggregateId);
        String eventId = event.getSerializedObject().getMessageId();
        Long eventIndex = event.getSerializedObject().getIndex();
        Instant timestamp = ofEpochMilli(event.getSerializedObject().getTimestamp());
        RefreshingAggregateRoot<T> aggregate = cache.getIfPresent(cacheKey);

        if (aggregate == null || aggregate.status == Status.UNVERIFIED) {
            aggregate =
                    Optional.ofNullable(delegate.load(aggregateId, type, true, false))
                            .map(a -> new RefreshingAggregateRoot<>(
                                    a.get(), handler, serializer, a.id(), a.type(),
                                    a.previous(), a.lastEventId(), a.lastEventIndex(), a.timestamp(),
                                    Objects.equals(a.lastEventId(), eventId)
                                            ? Status.IN_SYNC :
                                            Status.AHEAD))
                            .orElseGet(() -> {
                                log.warn("Delegate repository did not contain aggregate with id {} of type {}",
                                         aggregateId, type);
                                return null;
                            });
        } else if (aggregate.status == Status.IN_SYNC) {
            try {
                aggregate =
                        new RefreshingAggregateRoot<>(handler.invoke(aggregate.get(), event), handler, serializer,
                                                      aggregate.id(), aggregate.type(), aggregate, eventId,
                                                      eventIndex, timestamp, Status.IN_SYNC);
            } catch (Exception e) {
                log.error("Failed to update aggregate with id {} of type {}", aggregateId, type, e);
                aggregate = null;
            }
        } else if (eventId.equals(aggregate.lastEventId)) {
            aggregate = aggregate.toBuilder().status(Status.IN_SYNC).build();
        }

        if (aggregate == null) {
            cache.invalidate(cacheKey);
        } else {
            cache.put(cacheKey, aggregate);
        }
    }

    @Override
    public boolean supports(Class<?> aggregateType) {
        return delegate.supports(aggregateType);
    }

    @Override
    public boolean cachingAllowed(Class<?> aggregateType) {
        return delegate.cachingAllowed(aggregateType);
    }

    @AllArgsConstructor
    @Value
    @Accessors(fluent = true)
    @Builder(toBuilder = true)
    private static class RefreshingAggregateRoot<T> implements AggregateRoot<T> {
        @ToString.Exclude
        T model;
        EventSourcingHandler<T> handler;
        Serializer serializer;
        String id;
        Class<T> type;
        @ToString.Exclude
        AggregateRoot<T> previous;
        String lastEventId;
        Long lastEventIndex;
        Instant timestamp;
        Status status;

        @Override
        public T get() {
            return model;
        }

        @Override
        public AggregateRoot<T> apply(Message eventMessage) {
            throw new UnsupportedOperationException(
                    format("Not allowed to apply a %s. The aggregate is readonly.", eventMessage));
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
                    RefreshingAggregateRoot<T> result = this;
                    Iterator<Object> iterator = Arrays.stream(commands).iterator();
                    while (iterator.hasNext()) {
                        Object c = iterator.next();
                        ValidationUtils.assertLegal(c, result.get());
                        if (iterator.hasNext()) {
                            result = result.forceApply(c);
                        }
                    }
                    return this;
            }
        }

        private RefreshingAggregateRoot<T> forceApply(Object event) {
            Message message = event instanceof Message ? (Message) event : new Message(event);
            Message eventMessage = message.withMetadata(
                    message.getMetadata().with(AggregateRoot.AGGREGATE_ID_METADATA_KEY, id,
                                               AggregateRoot.AGGREGATE_TYPE_METADATA_KEY, type.getName()));
            DeserializingMessage deserializingMessage = new DeserializingMessage(new DeserializingObject<>(
                    eventMessage.serialize(serializer), type -> serializer.convert(eventMessage.getPayload(), type)),
                                                                                 EVENT);
            return new RefreshingAggregateRoot<>(handler.invoke(model, deserializingMessage), handler, serializer,
                                                 id, type, this, eventMessage.getMessageId(),
                                                 null, timestamp, status);
        }
    }

    private enum Status {
        IN_SYNC, AHEAD, UNVERIFIED
    }
}
