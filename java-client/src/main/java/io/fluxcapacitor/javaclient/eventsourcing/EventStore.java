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

package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.Message;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface EventStore {

    default Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber, Message... events) {
        return storeEvents(aggregateId, domain, lastSequenceNumber, Arrays.asList(events));
    }

    Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber, List<Message> events);

    default Stream<Message> getEvents(String aggregateId) {
        return getEvents(aggregateId, -1L);
    }

    Stream<Message> getEvents(String aggregateId, long lastSequenceNumber);


    void storeSnapshot(Snapshot snapshot);

    Optional<Snapshot> getSnapshot(String aggregateId);

    void deleteSnapshot(String aggregateId);

}
