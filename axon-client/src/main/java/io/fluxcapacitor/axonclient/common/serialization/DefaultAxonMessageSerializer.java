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

package io.fluxcapacitor.axonclient.common.serialization;

import io.fluxcapacitor.axonclient.eventhandling.SerializedSnapshot;
import io.fluxcapacitor.common.api.SerializedMessage;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventsourcing.DomainEventMessage;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.MessageSerializer;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.upcasting.event.EventUpcasterChain;

import java.util.stream.Stream;

import static org.axonframework.eventsourcing.eventstore.EventUtils.upcastAndDeserializeDomainEvents;
import static org.axonframework.eventsourcing.eventstore.EventUtils.upcastAndDeserializeTrackedEvents;

public class DefaultAxonMessageSerializer implements AxonMessageSerializer {
    private final MessageSerializer delegate;
    private final EventUpcasterChain upcasterChain;

    public DefaultAxonMessageSerializer(Serializer delegate, EventUpcasterChain eventUpcasterChain) {
        this.delegate = new MessageSerializer(delegate);
        upcasterChain = eventUpcasterChain;
    }

    @Override
    public byte[] serialize(Message<?> message) {
        return serialize(createBuilder(message, delegate).build());
    }

    @Override
    public byte[] serializeCommand(CommandMessage<?> message) {
        return serialize(createBuilder(message, delegate).commandName(message.getCommandName()).build());
    }

    @Override
    public byte[] serializeEvent(EventMessage<?> message) {
        if (message instanceof DomainEventMessage<?>) {
            return serializeDomainEvent((DomainEventMessage<?>) message);
        }
        return serialize(createBuilder(message, delegate).timestamp(message.getTimestamp().toEpochMilli()).build());
    }

    @Override
    public byte[] serializeDomainEvent(DomainEventMessage<?> message) {
        return serialize(createBuilder(message, delegate).timestamp(message.getTimestamp().toEpochMilli())
                                 .domain(message.getType()).aggregateId(message.getAggregateIdentifier())
                                 .sequenceNumber(message.getSequenceNumber())
                                 .build());
    }

    @Override
    public Message<?> deserializeMessage(SerializedMessage message) {
        return toMessage(deserialize(message.getData().getValue()));
    }

    @Override
    public DomainEventMessage<?> deserializeSnapshot(SerializedSnapshot snapshot) {
        return upcastAndDeserializeDomainEvents(
                Stream.of(new AxonDomainEventEntry(deserialize(snapshot.getData().getValue()))), delegate, upcasterChain,
                false).peek();
    }

    @Override
    public CommandMessage<?> deserializeCommand(SerializedMessage message) {
        AxonMessage axonMessage = deserialize(message.getData().getValue());
        return new GenericCommandMessage<>(toMessage(axonMessage), axonMessage.getCommandName());
    }

    @Override
    public Stream<? extends TrackedEventMessage<?>> deserializeEvents(
            Stream<SerializedMessage> messageStream) {
        return upcastAndDeserializeTrackedEvents(messageStream.map(m -> new AxonEventEntry(
                new IndexTrackingToken(m.getIndex()), deserialize(m.getData().getValue()))), delegate, upcasterChain, false);
    }

    @Override
    public DomainEventStream deserializeDomainEvents(Stream<SerializedMessage> messageStream) {
        return upcastAndDeserializeDomainEvents(
                messageStream.map(m -> new AxonDomainEventEntry(deserialize(m.getData().getValue()))), delegate, upcasterChain,
                false);
    }

    protected AxonMessage.Builder createBuilder(Message message, MessageSerializer serializer) {
        SerializedObject<byte[]> payload = serializer.serializePayload(message, byte[].class);
        SerializedObject<byte[]> metadata = serializer.serializeMetaData(message, byte[].class);
        return AxonMessage.builder().id(message.getIdentifier()).payload(payload.getData())
                .type(payload.getType().getName())
                .revision(payload.getType().getRevision()).metadata(metadata.getData());
    }

    private Message<?> toMessage(AxonMessage axonMessage) {
        Object payload = delegate.deserialize(new SimpleSerializedObject<>(
                axonMessage.getPayload(), byte[].class, axonMessage.getType(), axonMessage.getRevision()));
        MetaData metadata = delegate.deserialize(new SimpleSerializedObject<>(
                axonMessage.getMetadata(), byte[].class, MetaData.class.getName(), null));
        return new GenericMessage<>(axonMessage.getId(), payload, metadata);
    }

    private byte[] serialize(AxonMessage axonMessage) {
        return delegate.serialize(axonMessage, byte[].class).getData();
    }

    private AxonMessage deserialize(byte[] bytes) {
        return delegate
                .deserialize(new SimpleSerializedObject<>(bytes, byte[].class, AxonMessage.class.getName(), null));
    }
}
