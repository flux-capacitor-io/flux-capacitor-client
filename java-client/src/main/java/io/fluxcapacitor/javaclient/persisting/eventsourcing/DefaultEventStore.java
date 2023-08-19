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

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.api.SerializedMessage;
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
import java.util.concurrent.CompletableFuture;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
@Slf4j
public class DefaultEventStore implements EventStore {
    private final EventStoreClient client;
    private final Serializer serializer;
    private final DispatchInterceptor dispatchInterceptor;
    @Delegate
    private final HandlerRegistry localHandlerRegistry;

    @Override
    public CompletableFuture<Void> storeEvents(Object aggregateId, List<?> events, boolean storeOnly,
                                               boolean interceptBeforeStoring) {
        CompletableFuture<Void> result;
        List<DeserializingMessage> messages = new ArrayList<>(events.size());
        try {
            int segment = ConsistentHashing.computeSegment(aggregateId.toString());
            events.forEach(e -> {
                DeserializingMessage deserializingMessage;
                if (e instanceof DeserializingMessage) {
                    deserializingMessage = (DeserializingMessage) e;
                } else if (interceptBeforeStoring) {
                    Message m = dispatchInterceptor.interceptDispatch(Message.asMessage(e), EVENT);
                    SerializedMessage serializedMessage
                            = dispatchInterceptor.modifySerializedMessage(m.serialize(serializer), m, EVENT);
                    deserializingMessage = new DeserializingMessage(serializedMessage, type -> m.getPayload(), EVENT);
                } else {
                    deserializingMessage = new DeserializingMessage(Message.asMessage(e), EVENT, serializer);
                }
                messages.add(deserializingMessage);
            });
            result = client.storeEvents(aggregateId.toString(),
                                        messages.stream().map(m -> m.getSerializedObject().getSegment() == null ?
                                                        m.getSerializedObject().withSegment(segment) : m.getSerializedObject())
                                                .collect(toList()), storeOnly);
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store events %s for aggregate %s", events.stream().map(
                    DefaultEventStore::payloadName).collect(toList()), aggregateId), e);
        }
        messages.forEach(localHandlerRegistry::handle);
        return result;
    }

    private static String payloadName(Object event) {
        return event instanceof Message ? ((Message) event).getPayloadClass().getSimpleName()
                : event == null ? "null" : event.getClass().getSimpleName();
    }

    @Override
    public AggregateEventStream<DeserializingMessage> getEvents(Object aggregateId, long lastSequenceNumber,
                                                                boolean ignoreUnknownType) {
        try {
            AggregateEventStream<SerializedMessage> serializedEvents =
                    client.getEvents(aggregateId.toString(), lastSequenceNumber);
            return serializedEvents.convert(stream -> serializer.deserializeMessages(stream, EVENT, !ignoreUnknownType));
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to obtain events for aggregate %s", aggregateId), e);
        }
    }
}
