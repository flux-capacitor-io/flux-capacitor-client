/*
 * Copyright (c) 2016-2021 Flux Capacitor.
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
import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.List;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public class DefaultEventStore implements EventStore {
    private final EventStoreClient client;
    private final EventStoreSerializer serializer;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public Awaitable storeEvents(String aggregateId, String domain, long lastSequenceNumber,
                                 List<?> events, boolean storeOnly) {
        Awaitable result;
        List<DeserializingMessage> messages = new ArrayList<>(events.size());
        try {
            int segment = ConsistentHashing.computeSegment(aggregateId);
            events.forEach(e -> {
                DeserializingMessage deserializingMessage;
                if (e instanceof DeserializingMessage) {
                    deserializingMessage = (DeserializingMessage) e;
                } else {
                    Message message = e instanceof Message ? (Message) e : new Message(e);
                    deserializingMessage = new DeserializingMessage(serializer.serialize(message), type -> serializer
                            .convert(message.getPayload(), type), EVENT);
                }
                messages.add(deserializingMessage);
            });
            result = client.storeEvents(aggregateId, domain, lastSequenceNumber,
                                        messages.stream().map(m -> m.getSerializedObject().withSegment(segment))
                                                .collect(toList()), storeOnly);
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store events %s for aggregate %s", events, aggregateId),
                                             e);
        }
        messages.forEach(m -> localHandlerRegistry.handle(m.getPayload(), m.getSerializedObject()));
        return result;
    }

    @Override
    public AggregateEventStream<DeserializingMessage> getEvents(String aggregateId, long lastSequenceNumber) {
        try {
            AggregateEventStream<SerializedMessage> serializedEvents =
                    client.getEvents(aggregateId, lastSequenceNumber);
            return serializedEvents.convert(serializer::deserializeDomainEvents);
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to obtain domain events for aggregate %s", aggregateId), e);
        }
    }
}
