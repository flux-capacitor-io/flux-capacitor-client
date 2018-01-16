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

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.repository.Repository;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiFunction;

@Slf4j
public class EventSourcingRepository<T> implements Repository<T> {

    private final EventStoreClient eventStoreClient;
    private final BiFunction<T, SerializedMessage, T> eventHandler;

    public EventSourcingRepository(EventStoreClient eventStoreClient, BiFunction<T, SerializedMessage, T> eventHandler) {
        this.eventStoreClient = eventStoreClient;
        this.eventHandler = eventHandler;
    }

    @Override
    public T get(Object id) {
        String aggregateId = id.toString();
        Optional<Snapshot> snapshot = eventStoreClient.getSnapshot(aggregateId);
        T value = null;
        try {
            value = snapshot.map(s -> eventHandler.apply(null, new SerializedMessage(s.getData()))).orElse(null);
        } catch (SerializationException e) {
            log.warn("Failed to load snapshot for aggregate {}. Deleting existing one.", id, e);
            snapshot = Optional.empty();
            eventStoreClient.deleteSnapshot(aggregateId);
        }
        Iterator<SerializedMessage> messageIterator =
                snapshot.map(s -> eventStoreClient.getEvents(aggregateId, s.getLastSequenceNumber()))
                        .orElse(eventStoreClient.getEvents(aggregateId, -1L)).iterator();
        while (messageIterator.hasNext()) {
            value = eventHandler.apply(value, messageIterator.next());
        }
        return value;
    }

    @Override
    public void delete(Object id) {
        //no op
    }

    @Override
    public void put(Object id, T value) {
        //no op
    }
}
