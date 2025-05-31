/*
 * Copyright (c) Flux Capacitor IP B.V. or its affiliates. All Rights Reserved.
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

package io.fluxcapacitor.javaclient.persisting.repository;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.configuration.FluxCapacitorBuilder;
import io.fluxcapacitor.javaclient.configuration.client.Client;
import io.fluxcapacitor.javaclient.modeling.Entity;
import io.fluxcapacitor.javaclient.persisting.caching.Cache;
import io.fluxcapacitor.javaclient.tracking.ConsumerConfiguration;
import io.fluxcapacitor.javaclient.tracking.IndexUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static io.fluxcapacitor.common.MessageType.NOTIFICATION;
import static io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage.handleBatch;
import static io.fluxcapacitor.javaclient.modeling.Entity.getAggregateId;
import static io.fluxcapacitor.javaclient.modeling.Entity.getAggregateType;
import static io.fluxcapacitor.javaclient.tracking.client.DefaultTracker.start;
import static java.lang.String.format;

/**
 * A wrapper around a delegate {@link AggregateRepository} that ensures cached aggregates stay in sync with the event
 * log.
 * <p>
 * This repository starts an internal {@link io.fluxcapacitor.javaclient.tracking.client.TrackingClient} that tails the
 * global event log. It deserializes received events and applies them to any corresponding aggregate in the cache,
 * thereby ensuring that all cached aggregates reflect the latest known state.
 *
 * <p>This design makes it possible to load up-to-date aggregates within event and notification handlers, allowing
 * these types of handlers to rely on the event model as a read model. This enables event-sourced read models without
 * requiring the developer to maintain separate query-oriented state.
 *
 * <p>Specifically, when an event handler loads an aggregate, the repository first ensures that all events up to and
 * including the event being handled have been processed by the tracker and applied to the cache. This prevents race
 * conditions and stale reads.
 *
 * <p>If a cache miss occurs, the aggregate is loaded from the delegate repository and cached for future access.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Transparent event replay on cached aggregates.</li>
 *   <li>Ensures correct ordering and consistency during event handling via index synchronization.</li>
 *   <li>Relationship metadata is kept up to date using {@link Relationship} updates.</li>
 *   <li>Configurable slow-tracking detection via {@link #slowTrackingThreshold} (default 10 seconds).</li>
 * </ul>
 *
 * <p>This caching repository is enabled by default in all Flux Capacitor applications to support aggregate access
 * during event processing. If needed, it can be disabled using
 * {@link FluxCapacitorBuilder#disableAutomaticAggregateCaching()}, in which case aggregate state will not be tracked
 * or kept in sync automatically, and developers are expected to manage consistency manually.
 *
 * @see AggregateRepository
 * @see Entity
 * @see io.fluxcapacitor.javaclient.tracking.client.TrackingClient
 */
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
    public <T> Entity<T> load(@NonNull Object aggregateId, Class<T> type) {
        catchUpIfNeeded();
        return delegate.load(aggregateId, type);
    }

    @Override
    public <T> Entity<T> loadFor(@NonNull Object entityId, Class<?> defaultType) {
        catchUpIfNeeded();
        return delegate.loadFor(entityId, defaultType);
    }

    @Override
    public <T> Entity<T> asEntity(T entityId) {
        return delegate.asEntity(entityId);
    }

    @Override
    public CompletableFuture<Void> repairRelationships(Entity<?> aggregate) {
        return delegate.repairRelationships(aggregate);
    }

    @Override
    public Map<String, Class<?>> getAggregatesFor(Object entityId) {
        catchUpIfNeeded();
        return delegate.getAggregatesFor(entityId);
    }

    @Override
    public CompletableFuture<Void> deleteAggregate(Object aggregateId) {
        return delegate.deleteAggregate(aggregateId);
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
        if (id != null) {
            try {
                if (Objects.equals(client.id(), m.getSerializedObject().getSource())) {
                    cache.<Entity<?>>computeIfPresent(id, (i, a) -> {
                        try {
                            return a.withEventIndex(m.getIndex(), m.getMessageId());
                        } catch (Throwable e) {
                            log.error("Failed to update event index {} for aggregate {}"
                                      + " (id {}, last event id {}). Clearing aggregate from cache.",
                                      m.getMessageId(), getAggregateType(m), id, a.lastEventId(), e);
                            return null;
                        }
                    });
                } else {
                    long index = m.getIndex();
                    cache.<Entity<?>>computeIfPresent(
                            id, (i, before) -> {
                                Long lastIndex = before.highestEventIndex();
                                if (lastIndex == null || lastIndex < index) {
                                    boolean wasLoading = Entity.isLoading();
                                    try {
                                        Entity.loading.set(true);
                                        try {
                                            Entity<?> after = before.apply(m);
                                            updateRelationships(before, after);
                                            return after;
                                        } catch (Throwable e) {
                                            log.error("Failed to handle event {} for aggregate {}"
                                                      + " (id {}, last event id {}). Clearing aggregate from cache.",
                                                      m.getMessageId(), getAggregateType(m), id, before.lastEventId(),
                                                      e);
                                            return null;
                                        }
                                    } finally {
                                        Entity.loading.set(wasLoading);
                                    }
                                }
                                return before;
                            });
                }
            } catch (Throwable e) {
                log.error("Failed to handle event {} for aggregate {} (id {})", m.getMessageId(),
                          getAggregateType(m), id, e);
            }
        }
    }

    protected void updateRelationships(Entity<?> before, Entity<?> after) {
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
                case EVENT, NOTIFICATION -> {
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
                }
            }
        }
    }

    protected void startTrackerIfNeeded() {
        if (started.compareAndSet(false, true)) {
            start(this::handleEvents, NOTIFICATION, ConsumerConfiguration.builder()
                    .ignoreSegment(true)
                    .clientControlledIndex(true)
                    .minIndex(
                            lastEventIndex = IndexUtils.indexFromTimestamp(FluxCapacitor.currentTime().minusSeconds(2)))
                    .name(format("%s_%s", client.name(), CachingAggregateRepository.class.getSimpleName()))
                    .build(), client);
            synchronized (cache) {
                cache.notifyAll();
            }
        }
    }

}
