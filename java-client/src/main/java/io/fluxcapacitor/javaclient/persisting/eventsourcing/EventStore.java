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

public interface EventStore extends HasLocalHandlers {

    default CompletableFuture<Void> storeEvents(Object aggregateId, Object... events) {
        return storeEvents(aggregateId, asList(events));
    }

    default CompletableFuture<Void> storeEvents(Object aggregateId, List<?> events) {
        return storeEvents(aggregateId, events, EventPublicationStrategy.STORE_AND_PUBLISH);
    }

    CompletableFuture<Void> storeEvents(Object aggregateId, List<?> events, EventPublicationStrategy strategy);

    default AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId) {
        return getEvents(aggregateId, -1L);
    }

    default AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber) {
        return getEvents(aggregateId, lastSequenceNumber, -1);
    }

    default AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber,
                                                                 int maxSize) {
        return getEvents(aggregateId, lastSequenceNumber, maxSize, false);
    }

    AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber, int maxSize,
                                                         boolean ignoreUnknownType);
}
