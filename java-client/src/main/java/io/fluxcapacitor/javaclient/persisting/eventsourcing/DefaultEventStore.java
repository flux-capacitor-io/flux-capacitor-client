package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.handling.HandlerConfiguration;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerRegistry;
import lombok.AllArgsConstructor;
import lombok.experimental.Delegate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    public Awaitable storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber,
                                       List<?> events) {
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
                    deserializingMessage = new DeserializingMessage(serializer.serialize(message),
                                                                    message::getPayload, EVENT);
                }
                messages.add(deserializingMessage);
            });
            result = client.storeEvents(aggregateId, domain, lastSequenceNumber,
                               messages.stream().map(m -> m.getSerializedObject().withSegment(segment))
                                       .collect(toList()));
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store events %s for aggregate %s", events, aggregateId),
                                             e);
        }
        messages.forEach(m -> localHandlerRegistry.handle(m.getPayload(), m.getSerializedObject()));
        return result;
    }

    @Override
    public Stream<DeserializingMessage> getDomainEvents(String aggregateId, long lastSequenceNumber) {
        try {
            return serializer.deserializeDomainEvents(client.getEvents(aggregateId, lastSequenceNumber));
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to obtain domain events for aggregate %s", aggregateId), e);
        }
    }
}
