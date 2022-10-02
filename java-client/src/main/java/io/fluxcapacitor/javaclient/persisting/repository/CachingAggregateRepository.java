package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;
import static io.fluxcapacitor.javaclient.modeling.Entity.getAggregateId;
import static io.fluxcapacitor.javaclient.modeling.Entity.getAggregateType;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;

@RequiredArgsConstructor
@Slf4j
public class CachingAggregateRepository implements AggregateRepository {
    public static Duration slowTrackingThreshold = Duration.ofSeconds(10L);

    private final AggregateRepository delegate;
    private final Client client;
    private final Cache cache;
    private final Cache relationshipsCache;
    private final Serializer serializer;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile long lastEventIndex = -1L;

    @Override
    public <T> Entity<T> load(String aggregateId, Class<T> type) {
        catchUpIfNeeded();
        return delegate.load(aggregateId, type);
    }

    @Override
    public <T> Entity<T> loadFor(String entityId, Class<?> defaultType) {
        catchUpIfNeeded();
        return delegate.loadFor(entityId, defaultType);
    }

    @Override
    public Map<String, Class<?>> getAggregatesFor(String entityId) {
        return delegate.getAggregatesFor(entityId);
    }

    protected void handleEvents(List<SerializedMessage> messages) {
        try {
            handleBatch(serializer.deserializeMessages(messages.stream(), EVENT)).forEach(this::handleEvent);
        } finally {
            messages.stream().reduce((a, b) -> b).map(SerializedMessage::getIndex).ifPresent(index -> {
                lastEventIndex = index;
                synchronized (cache) {
                    cache.notifyAll();
                }
            });
        }
    }

    private void handleEvent(DeserializingMessage m) {
        String id = getAggregateId(m);
        Class<?> type = getAggregateType(m);
        if (id != null && type != null && cachingAllowed(type)) {
            try {
                if (Objects.equals(client.id(), m.getSerializedObject().getSource())) {
                    cache.<ImmutableAggregateRoot<?>>computeIfPresent(id, (i, a) -> a.withEventIndex(
                            m.getIndex(), m.getMessageId()));
                } else {
                    long index = m.getIndex();
                    delegate.load(id, type);
                    cache.<ImmutableAggregateRoot<?>>computeIfPresent(
                            id, (i, before) -> {
                                Long lastIndex = before.highestEventIndex();
                                if (lastIndex == null || lastIndex < index) {
                                    boolean wasLoading = Entity.isLoading();
                                    try {
                                        Entity.loading.set(true);
                                        ImmutableAggregateRoot<?> after = before.apply(m);
                                        updateRelationships(before, after);
                                        return after;
                                    } finally {
                                        Entity.loading.set(wasLoading);
                                    }
                                }
                                return before;
                            });
                }
            } catch (Exception e) {
                log.error("Failed to handle event {} for aggregate {} (id {})", m.getMessageId(), type, id, e);
            }
        }
    }

    protected void updateRelationships(ImmutableAggregateRoot<?> before, ImmutableAggregateRoot<?> after) {
        Set<Relationship> associations = after.associations(before), dissociations = after.dissociations(before);
        dissociations.forEach(
                r -> relationshipsCache.<Map<String, String>>computeIfPresent(r.getEntityId(), (id, map) -> {
                    map.remove(r.getAggregateId());
                    return map;
                }));
        associations.forEach(
                r -> relationshipsCache.<Map<String, Class<?>>>computeIfPresent(r.getEntityId(), (id, map) -> {
                    map.put(r.getAggregateId(), after.type());
                    return map;
                }));
    }

    protected void catchUpIfNeeded() {
        startTrackerIfNeeded();
        DeserializingMessage current = DeserializingMessage.getCurrent();
        if (current != null && !Entity.isLoading()) {
            switch (current.getMessageType()) {
                case EVENT:
                case NOTIFICATION:
                    Long eventIndex = current.getIndex();
                    if (eventIndex != null && lastEventIndex < eventIndex) {
                        synchronized (cache) {
                            Instant start = Instant.now();
                            while (lastEventIndex < eventIndex) {
                                try {
                                    cache.wait(5_000);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Failed to load aggregate for event " + current, e);
                                }
                            }
                            Duration fetchDuration = Duration.between(start, Instant.now());
                            if (fetchDuration.compareTo(slowTrackingThreshold) > 0) {
                                log.warn("It took over {} for the aggregate repo tracking to get in sync. This may "
                                                 + "indicate that the repo has trouble keeping up.", fetchDuration);
                            }
                        }
                    }
                    break;
            }
        }
    }

    protected void startTrackerIfNeeded() {
        if (started.compareAndSet(false, true)) {
            start(this::handleEvents, ConsumerConfiguration.builder().messageType(NOTIFICATION)
                    .minIndex(lastEventIndex = IndexUtils.indexForCurrentTime())
                    .name(CachingAggregateRepository.class.getSimpleName()).build(), client);
            synchronized (cache) {
                cache.notifyAll();
            }
        }
    }

    @Override
    public boolean cachingAllowed(Class<?> aggregateType) {
        return delegate.cachingAllowed(aggregateType);
    }

}
