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
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

import java.util.List;

import static java.util.Arrays.asList;

public interface EventStore extends HasLocalHandlers {

    default Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber, Object... events) {
        return storeEvents(aggregateId, domain, lastSequenceNumber, asList(events));
    }

    Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber, List<?> events);

    default AggregateEventStream<DeserializingMessage> getEvents(String aggregateId) {
        return getEvents(aggregateId, -1L);
    }

    AggregateEventStream<DeserializingMessage> getEvents(String aggregateId, long lastSequenceNumber);
}
