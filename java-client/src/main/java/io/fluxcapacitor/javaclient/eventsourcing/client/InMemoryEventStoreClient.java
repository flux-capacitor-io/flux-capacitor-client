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

package io.fluxcapacitor.javaclient.eventsourcing.client;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.api.eventsourcing.EventBatch;
import io.fluxcapacitor.javaclient.tracking.client.InMemoryMessageStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class InMemoryEventStoreClient extends InMemoryMessageStore implements EventStoreClient {

    private final Map<String, List<EventBatch>> domainEvents = new ConcurrentHashMap<>();

    @Override
    public Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber,
                                 List<SerializedMessage> events) {
        domainEvents.compute(aggregateId, (id, list) -> {
            if (list == null) {
                list = new ArrayList<>();
            }
            list.add(new EventBatch(aggregateId, domain, lastSequenceNumber, events));
            return list;
        });
        return super.send(events.toArray(new SerializedMessage[0]));
    }

    @Override
    public Stream<SerializedMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        return domainEvents.getOrDefault(aggregateId, Collections.emptyList()).stream()
                .filter(batch -> batch.getLastSequenceNumber() > lastSequenceNumber)
                .flatMap(batch -> {
                    List<SerializedMessage> events = batch.getEvents();
                    if (batch.getFirstSequenceNumber() > lastSequenceNumber) {
                        return events.stream();
                    }
                    return events.stream().skip(lastSequenceNumber - batch.getFirstSequenceNumber() + 1);
                });
    }

}
