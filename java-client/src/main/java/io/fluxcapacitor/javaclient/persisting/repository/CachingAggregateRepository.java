package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.IndexUtils;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.AggregateRoot;
import io.fluxcapacitor.javaclient.modeling.ImmutableAggregateRoot;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;
import static io.fluxcapacitor.javaclient.modeling.AggregateIdResolver.getAggregateId;
import static io.fluxcapacitor.javaclient.modeling.AggregateTypeResolver.getAggregateType;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;

@RequiredArgsConstructor
@Slf4j
public class CachingAggregateRepository implements AggregateRepository {
    public static Duration slowTrackingThreshold = Duration.ofSeconds(10L);

    private final AggregateRepository delegate;
    private final Client client;
    private final Cache cache;
    private final Serializer serializer;

    private final AtomicBoolean started = new AtomicBoolean();
    private volatile long lastEventIndex = -1L;

    @Override
    public <T> AggregateRoot<T> load(String aggregateId, Class<T> type) {
        catchUpIfNeeded();
        return delegate.load(aggregateId, type);
    }

    @Override
    public <T> AggregateRoot<T> loadFor(String entityId, Class<?> defaultType) {
        catchUpIfNeeded();
        return delegate.loadFor(entityId, defaultType);
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
                            id, (i, a) -> Optional.ofNullable(a.highestEventIndex())
                                    .filter(lastIndex -> lastIndex < index)
                                    .<ImmutableAggregateRoot<?>>map(li -> a.apply(m)).orElse(a));
                }
            } catch (Exception e) {
                log.error("Failed to handle event {} for aggregate {} (id {})", m.getMessageId(), type, id, e);
            }
        }
    }

    protected void catchUpIfNeeded() {
        startTrackerIfNeeded();
        DeserializingMessage current = DeserializingMessage.getCurrent();
        if (current != null) {
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
                    .lastIndex(lastEventIndex = IndexUtils.indexForCurrentTime())
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
