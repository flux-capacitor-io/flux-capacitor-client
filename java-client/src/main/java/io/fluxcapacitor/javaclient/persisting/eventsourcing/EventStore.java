/*
 * Copyright (c) 2016-2020 Flux Capacitor.
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
import io.fluxcapacitor.common.Guarantee;
import io.fluxcapacitor.common.api.modeling.Relationship;
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;

public interface EventStore extends HasLocalHandlers {

    default Awaitable storeEvents(String aggregateId, Object... events) {
        return storeEvents(aggregateId, asList(events));
    }

    default Awaitable storeEvents(String aggregateId, List<?> events) {
        return storeEvents(aggregateId, events, false);
    }

    Awaitable storeEvents(String aggregateId, List<?> events, boolean storeOnly);

    default AggregateEventStream<DeserializingMessage> getEvents(String aggregateId) {
        return getEvents(aggregateId, -1L);
    }

    AggregateEventStream<DeserializingMessage> getEvents(String aggregateId, long lastSequenceNumber);

    Awaitable updateRelationships(UpdateRelationships updateRelationships);

    default Awaitable updateRelationships(Set<Relationship> associations, Set<Relationship> dissociations) {
        return updateRelationships(new UpdateRelationships(associations, dissociations, Guarantee.STORED));
    }

    Map<String, Class<?>> getAggregatesFor(String entityId);
}
