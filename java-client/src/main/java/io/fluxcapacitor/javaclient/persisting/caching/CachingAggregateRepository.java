package io.fluxcapacitor.javaclient.persisting.caching;

import io.fluxcapacitor.common.MessageType;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.modeling.Aggregate;
import io.fluxcapacitor.javaclient.modeling.AggregateRepository;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandler;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.EventSourcingHandlerFactory;
import io.fluxcapacitor.javaclient.tracking.client.TrackingClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.javaclient.modeling.AggregateIdResolver.getAggregateId;
import static io.fluxcapacitor.javaclient.modeling.AggregateTypeResolver.getAggregateType;
import static io.fluxcapacitor.javaclient.tracking.client.TrackingUtils.start;
import static java.lang.String.format;
import static java.time.Instant.ofEpochMilli;

@RequiredArgsConstructor
@Slf4j
public class CachingAggregateRepository implements AggregateRepository {
    private static final Function<String, String> keyFunction = aggregateId ->
            CachingAggregateRepository.class.getSimpleName() + ":" + aggregateId;

    private final AggregateRepository delegate;
    private final EventSourcingHandlerFactory handlerFactory;
    private final Cache cache;

    private final String clientName;
    private final TrackingClient trackingClient;
    private final Serializer serializer;

    private final AtomicReference<Instant> started = new AtomicReference<>();
    private final AtomicLong lastEventIndex = new AtomicLong();

    @Override
    public <T> Aggregate<T> load(@NonNull String aggregateId, @NonNull Class<T> aggregateType, boolean onlyCached) {
        if (!delegate.cachingAllowed(aggregateType)) {
            return delegate.load(aggregateId, aggregateType, onlyCached);
        }
        DeserializingMessage current = DeserializingMessage.getCurrent();
        if (current != null && current.getMessageType() == MessageType.COMMAND) {
            return delegate.load(aggregateId, aggregateType, onlyCached);
        }
        Aggregate<T> result = delegate.load(aggregateId, aggregateType, true);
        if (result == null) {
            return Optional.<Aggregate<T>>ofNullable(doLoad(aggregateId, aggregateType, onlyCached))
                    .filter(a -> Optional.ofNullable(a.get()).map(m -> aggregateType.isAssignableFrom(m.getClass()))
                            .orElse(true))
                    .orElseGet(() -> delegate.load(aggregateId, aggregateType, onlyCached));
        }
        return result;
    }

    private <T> RefreshingAggregate<T> doLoad(String aggregateId, Class<T> type, boolean onlyCached) {
        if (started.compareAndSet(null, Instant.now())) {
            log.info("Start tracking notifications");
            start(format("%s_%s", clientName, CachingAggregateRepository.class.getSimpleName()),
                  trackingClient, this::handleEvents);
            return null;
        }
        if (lastEventIndex.get() <= 0 && started.get().isAfter(Instant.now().minusSeconds(5))) {
            return null; //make sure we don't miss any events before placing anything into the cache
        }
        DeserializingMessage current = DeserializingMessage.getCurrent();
        if (current != null && lastEventIndex.get() > 0) {
            switch (current.getMessageType()) {
                case EVENT:
                case NOTIFICATION:
                    Long eventIndex = current.getSerializedObject().getIndex();
                    if (eventIndex != null && lastEventIndex.get() < eventIndex) {
                        synchronized (cache) {
                            while (lastEventIndex.get() < eventIndex) {
                                try {
                                    cache.wait(5_000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    log.warn("Failed to load aggregate for event {}", current, e);
                                    return null;
                                }
                            }
                        }
                    }
                    break;
            }
        }
        if (onlyCached) {
            return cache.getIfPresent(aggregateId);
        }
        return cache.get(keyFunction.apply(aggregateId), id -> Optional.ofNullable(delegate.load(aggregateId, type))
                .map(a -> new RefreshingAggregate<>(a.get(), a.lastEventId(), a.timestamp(),
                                                    RefreshingAggregate.Status.UNVERIFIED))
                .orElse(null));
    }

    protected void handleEvents(List<SerializedMessage> messages) {
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
    }

    protected void handleEvent(DeserializingMessage event, String aggregateId, Class<?> type) {
        try {
            EventSourcingHandler<Object> handler = handlerFactory.forType(type);
            String cacheKey = keyFunction.apply(aggregateId);
            String eventId = event.getSerializedObject().getMessageId();
            Instant timestamp = ofEpochMilli(event.getSerializedObject().getTimestamp());
            RefreshingAggregate<?> aggregate = cache.getIfPresent(cacheKey);

            if (aggregate == null || aggregate.status == RefreshingAggregate.Status.UNVERIFIED) {
                if (handler.canHandle(null, event)) { //may be the first event for this aggregate
                    try {
                        aggregate = new RefreshingAggregate<>(handler.invoke(null, event), eventId,
                                                              timestamp, RefreshingAggregate.Status.IN_SYNC);
                    } catch (Exception ignored) {
                    }
                } else { //otherwise we just don't have it in the cache
                    aggregate =
                            Optional.ofNullable(delegate.load(aggregateId, type)).map(a -> new RefreshingAggregate<>(
                                    a.get(), a.lastEventId(), a.timestamp(), Objects.equals(a.lastEventId(), eventId)
                                    ? RefreshingAggregate.Status.IN_SYNC : RefreshingAggregate.Status.AHEAD))
                                    .orElseGet(() -> {
                                        log.warn("Delegate repository did not contain aggregate with id {} of type {}",
                                                 aggregateId, type);
                                        return null;
                                    });
                }
            } else if (aggregate.status == RefreshingAggregate.Status.IN_SYNC) {
                try {
                    aggregate = new RefreshingAggregate<>(handler.invoke(aggregate.get(), event), eventId,
                                                          timestamp, RefreshingAggregate.Status.IN_SYNC);
                } catch (Exception e) {
                    log.error("Failed to update aggregate with id {} of type {}", aggregateId, type, e);
                    aggregate = null;
                }
            } else if (eventId.equals(aggregate.lastEventId)) {
                aggregate = aggregate.toBuilder().status(RefreshingAggregate.Status.IN_SYNC).build();
            }

            if (aggregate == null) {
                cache.invalidate(cacheKey);
            } else {
                cache.put(cacheKey, aggregate);
            }
        } finally {
            this.lastEventIndex.updateAndGet(
                    i -> Optional.ofNullable(event.getSerializedObject().getIndex()).orElse(i));
            synchronized (cache) {
                cache.notifyAll();
            }
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
    private static class RefreshingAggregate<T> implements Aggregate<T> {
        T model;
        String lastEventId;
        Instant timestamp;
        Status status;

        @Override
        public T get() {
            return model;
        }

        @Override
        public Aggregate<T> apply(Message eventMessage) {
            throw new UnsupportedOperationException(
                    format("Not allowed to apply a %s. The aggregate is readonly.", eventMessage));
        }

        private enum Status {
            IN_SYNC, AHEAD, UNVERIFIED
        }
    }
}
