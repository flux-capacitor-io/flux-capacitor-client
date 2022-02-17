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
import io.fluxcapacitor.common.api.modeling.UpdateRelationships;
import io.fluxcapacitor.common.reflection.ReflectionUtils;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.common.serialization.Serializer;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.publishing.DispatchInterceptor;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@AllArgsConstructor
@Slf4j
public class DefaultEventStore implements EventStore {
    private final EventStoreClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public Awaitable storeEvents(String aggregateId, List<?> events, boolean storeOnly) {
        Awaitable result;
        List<DeserializingMessage> messages = new ArrayList<>(events.size());
        try {
            int segment = ConsistentHashing.computeSegment(aggregateId);
            events.forEach(e -> {
                DeserializingMessage deserializingMessage;
                if (e instanceof DeserializingMessage) {
                    deserializingMessage = (DeserializingMessage) e;
                } else {
                    Message m = dispatchInterceptor.interceptDispatch(Message.asMessage(e), EVENT);
                    SerializedMessage serializedMessage
                            = dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT);
                    deserializingMessage = new DeserializingMessage(serializedMessage, type -> m.getPayload(), EVENT);
                }
                FluxCapacitor.getOptionally()
                        .filter(fc -> deserializingMessage.getSerializedObject().getSource() == null)
                        .ifPresent(fc -> deserializingMessage.getSerializedObject().setSource(fc.client().id()));
                messages.add(deserializingMessage);
            });
            result = client.storeEvents(aggregateId,
                                        messages.stream().map(m -> m.getSerializedObject().getSegment() == null ?
                                                        m.getSerializedObject().withSegment(segment) : m.getSerializedObject())
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
            return serializedEvents.convert(stream -> serializer.deserializeMessages(stream, EVENT));
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to obtain events for aggregate %s", aggregateId), e);
        }
    }

    @Override
    public Awaitable updateRelationships(UpdateRelationships updateRelationships) {
        return client.updateRelationships(updateRelationships);
    }

    @Override
    public Map<String, Class<?>> getAggregatesFor(String entityId) {
        return client.getAggregateIds(entityId).entrySet().stream().collect(toMap(Map.Entry::getKey, e -> {
            try {
                return ReflectionUtils.classForName(serializer.upcastType(e.getValue()));
            } catch (Exception error) {
                log.error("Failed to get the aggregate class for type {} (aggregate id: {}, entity id: {})."
                          + " Please register a type caster with the Serializer.",
                          e.getValue(), e.getKey(), entityId, error);
                return Void.class;
            }
        }));
    }
}
