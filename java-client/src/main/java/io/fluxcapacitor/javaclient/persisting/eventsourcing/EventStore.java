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

package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.modeling.EventPublicationStrategy;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.Arrays.asList;

/**
 * High-level abstraction for accessing and storing domain events in an event-sourced system.
 * <p>
 * This interface allows writing and reading event streams associated with aggregate instances. It is responsible for
 * storing events produced by aggregates and retrieving event histories to rebuild aggregate state during rehydration.
 *
 * <p><strong>Usage Note:</strong> While this interface is public, it is rarely used directly. Applications typically
 * use higher-level abstractions such as:
 * <ul>
 *     <li>{@link io.fluxcapacitor.javaclient.FluxCapacitor#loadAggregate}</li>
 *     <li>{@link io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository}</li>
 *     <li>{@link io.fluxcapacitor.javaclient.modeling.Entity} instances</li>
 * </ul>
 *
 * @see io.fluxcapacitor.javaclient.persisting.repository.AggregateRepository
 * @see io.fluxcapacitor.javaclient.modeling.Entity
 */
public interface EventStore extends HasLocalHandlers {

    /**
     * Stores one or more events for a given aggregate using the default strategy
     * {@link EventPublicationStrategy#STORE_AND_PUBLISH}.
     *
     * @param aggregateId The ID of the aggregate.
     * @param events      One or more events to store.
     * @return A future that completes when the events have been successfully stored.
     */
    default CompletableFuture<Void> storeEvents(Object aggregateId, Object... events) {
        return storeEvents(aggregateId, asList(events));
    }

    /**
     * Stores a list of events for a given aggregate using the default strategy
     * {@link EventPublicationStrategy#STORE_AND_PUBLISH}.
     *
     * @param aggregateId The ID of the aggregate.
     * @param events      The list of events to store.
     * @return A future that completes when the events have been successfully stored.
     */
    default CompletableFuture<Void> storeEvents(Object aggregateId, List<?> events) {
        return storeEvents(aggregateId, events, EventPublicationStrategy.STORE_AND_PUBLISH);
    }

    /**
     * Stores a list of events for the given aggregate using a specified publication strategy.
     *
     * @param aggregateId The ID of the aggregate.
     * @param events      The events to store.
     * @param strategy    Whether to publish the events in addition to storing them.
     * @return A future that completes once the operation is acknowledged.
     */
    CompletableFuture<Void> storeEvents(Object aggregateId, List<?> events, EventPublicationStrategy strategy);

    /**
     * Retrieves the full event stream for an aggregate starting from the beginning.
     *
     * @param aggregateId The ID of the aggregate.
     * @return A stream of deserialized messages representing the event history of the aggregate.
     */
    default AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId) {
        return getEvents(aggregateId, -1L);
    }

    /**
     * Retrieves the event stream for an aggregate starting after the specified sequence number.
     *
     * @param aggregateId        The ID of the aggregate.
     * @param lastSequenceNumber The last known sequence number to resume from.
     * @return A stream of deserialized messages representing the event history of the aggregate.
     */
    default AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber) {
        return getEvents(aggregateId, lastSequenceNumber, -1);
    }

    /**
     * Retrieves the event stream for an aggregate starting after a given sequence number, with a maximum limit.
     *
     * @param aggregateId        The ID of the aggregate.
     * @param lastSequenceNumber The last known sequence number to resume from.
     * @param maxSize            The maximum number of events to return. A negative value means no limit.
     * @return A stream of deserialized messages representing the event history of the aggregate.
     */
    default AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber,
                                                                 int maxSize) {
        return getEvents(aggregateId, lastSequenceNumber, maxSize, false);
    }

    /**
     * Retrieves the event stream for an aggregate with full control over behavior.
     *
     * @param aggregateId        The ID of the aggregate.
     * @param lastSequenceNumber The last known sequence number to resume from.
     * @param maxSize            The maximum number of events to return.
     * @param ignoreUnknownType  Whether to ignore unknown payload types when deserializing events.
     * @return A stream of deserialized messages representing the event history of the aggregate.
     */
    AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber, int maxSize,
                                                         boolean ignoreUnknownType);
}
