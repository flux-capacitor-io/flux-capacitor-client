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

import io.fluxcapacitor.common.api.Message;
import io.fluxcapacitor.javaclient.common.repository.Repository;
import io.fluxcapacitor.javaclient.common.serialization.SerializationException;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.Optional;
import java.util.function.BiFunction;

@Slf4j
public class EventSourcingRepository<T> implements Repository<T> {

    private final EventStore eventStore;
    private final BiFunction<T, Message, T> eventHandler;

    public EventSourcingRepository(EventStore eventStore, BiFunction<T, Message, T> eventHandler) {
        this.eventStore = eventStore;
        this.eventHandler = eventHandler;
    }

    @Override
    public T get(Object id) {
        String aggregateId = id.toString();
        Optional<Snapshot> snapshot = eventStore.getSnapshot(aggregateId);
        T value = null;
        try {
            value = snapshot.map(s -> eventHandler.apply(null, new Message(s.getData()))).orElse(null);
        } catch (SerializationException e) {
            log.warn("Failed to load snapshot for aggregate {}. Deleting existing one.", id, e);
            snapshot = Optional.empty();
            eventStore.deleteSnapshot(aggregateId);
        }
        Iterator<Message> messageIterator =
                snapshot.map(s -> eventStore.getEvents(aggregateId, s.getLastSequenceNumber()))
                        .orElse(eventStore.getEvents(aggregateId)).iterator();
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
