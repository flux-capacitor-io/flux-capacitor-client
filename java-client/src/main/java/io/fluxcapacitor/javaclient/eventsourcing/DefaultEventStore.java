package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.eventsourcing.client.EventStoreClient;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

@AllArgsConstructor
public class DefaultEventStore implements EventStore {

    private final EventStoreClient client;
    private final EventStoreSerializer serializer;

    @Override
    public void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber,
                                  List<Message> events) {
        try {
            int segment = ConsistentHashing.computeSegment(aggregateId);
            client.storeEvents(aggregateId, domain, lastSequenceNumber, events.stream().map(serializer::serialize)
                    .map(e -> e.withSegment(segment)).collect(toList())).await();
        } catch (Exception e) {
            throw new EventSourcingException(format("Failed to store events %s for aggregate %s", events, aggregateId), e);
        }
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
