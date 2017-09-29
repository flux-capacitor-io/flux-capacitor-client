/*
 * Copyright (c) 2016-2017 Flux Capacitor.
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

package io.fluxcapacitor.axonclient.eventhandling;

import io.fluxcapacitor.axonclient.common.serialization.AxonMessageSerializer;
import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.javaclient.eventsourcing.Snapshot;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.common.Registration;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.eventsourcing.eventstore.TrackingEventStream;
import org.axonframework.eventsourcing.eventstore.TrackingToken;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Slf4j
public class FluxCapacitorEventStore extends AbstractEventBus implements EventStore {

    private final io.fluxcapacitor.javaclient.eventsourcing.EventStore delegate;
    private final AxonMessageSerializer serializer;

    public FluxCapacitorEventStore(io.fluxcapacitor.javaclient.eventsourcing.EventStore delegate, AxonMessageSerializer serializer) {
        this(NoOpMessageMonitor.INSTANCE, delegate, serializer);
    }

    public FluxCapacitorEventStore(MessageMonitor<? super EventMessage<?>> messageMonitor,
                                   io.fluxcapacitor.javaclient.eventsourcing.EventStore delegate, AxonMessageSerializer serializer) {
        super(messageMonitor);
        this.delegate = delegate;
        this.serializer = serializer;
    }

    protected void appendEvents(List<? extends EventMessage<?>> events) throws Exception {
        List<Message> convertedEvents = convert(events);
        for (int i = 0; i < events.size(); ) {
            String aggregateId = getAggregateId(events.get(i));
            int j = i + 1;
            while (j < events.size() && Objects.equals(aggregateId, getAggregateId(events.get(j)))) {
                j++;
            }
            EventMessage<?> lastEvent = events.get(j - 1);
            long sequenceNumber = 0L;
            String domain = null;
            if (lastEvent instanceof DomainEventMessage) {
                DomainEventMessage domainEvent = (DomainEventMessage) lastEvent;
                sequenceNumber = domainEvent.getSequenceNumber();
                domain = domainEvent.getType();
            }
            List<Message> group = convertedEvents.subList(i, j);
            delegate.storeEvents(aggregateId, domain, sequenceNumber, group).await();
            i = j;
        }
    }

    protected List<Message> convert(List<? extends EventMessage<?>> events) {
        return events.stream().map(e -> {
            Message message = new Message(serializer.serializeEvent(e));
            message.setSegment(ConsistentHashing.computeSegment(getAggregateId(e)));
            message.setType(e.getPayloadType().getName());
            return message;
        }).collect(toList());
    }

    private String getAggregateId(EventMessage<?> event) {
        return event instanceof DomainEventMessage ? ((DomainEventMessage) event).getAggregateIdentifier() : null;
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier) {
        Optional<DomainEventMessage<?>> optionalSnapshot;
        try {
            optionalSnapshot = delegate.getSnapshot(aggregateIdentifier).map(serializer::deserializeSnapshot);
        } catch (Exception | LinkageError e) {
            log.warn("Error reading snapshot. Reconstructing aggregate from entire event stream. Caused by: {} {}",
                     e.getClass().getName(), e.getMessage());
            optionalSnapshot = Optional.empty();
            delegate.deleteSnapshot(aggregateIdentifier);
        }
        return optionalSnapshot
                .map(snapshot -> DomainEventStream.concat(DomainEventStream.of(snapshot),
                                                          readEvents(aggregateIdentifier,
                                                                     snapshot.getSequenceNumber())))
                .orElse(readEvents(aggregateIdentifier, 0L));
    }

    protected Stream<? extends DomainEventMessage<?>> stagedDomainEventMessages(String aggregateIdentifier) {
        return queuedMessages().stream()
                .filter(m -> m instanceof DomainEventMessage)
                .map(m -> (DomainEventMessage<?>) m)
                .filter(m -> aggregateIdentifier.equals(m.getAggregateIdentifier()));
    }

    @Override
    public DomainEventStream readEvents(String aggregateIdentifier, long firstSequenceNumber) {
        return DomainEventStream.concat(
                serializer.deserializeDomainEvents(delegate.getEvents(aggregateIdentifier, firstSequenceNumber - 1)),
                DomainEventStream.of(stagedDomainEventMessages(aggregateIdentifier)
                                             .filter(m -> m.getSequenceNumber() >= firstSequenceNumber)));
    }

    @Override
    public void storeSnapshot(DomainEventMessage<?> snapshot) {
        byte[] bytes = serializer.serializeDomainEvent(snapshot);
        delegate.storeSnapshot(new Snapshot(snapshot.getAggregateIdentifier(), snapshot.getSequenceNumber(), bytes));
    }

    @Override
    protected void prepareCommit(List<? extends EventMessage<?>> events) {
        super.prepareCommit(events);
        try {
            appendEvents(events);
        } catch (Exception e) {
            throw new IllegalStateException("Could not append events " + events, e);
        }
    }

    @Override
    public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
        throw new UnsupportedOperationException("Subscribing event handlers are not supported");
    }

    @Override
    public TrackingEventStream openStream(TrackingToken trackingToken) {
        throw new UnsupportedOperationException("Tracking is supported via a dedicated event processor");
    }
}
