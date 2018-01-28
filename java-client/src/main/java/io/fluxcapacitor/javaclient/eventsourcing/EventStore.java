package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public interface EventStore {

    default void storeEvent(Object payload) {
        storeEvent(payload, Metadata.empty());
    }

    void storeEvent(Object payload, Metadata metadata);

    default void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, Message... events) {
        storeDomainEvents(aggregateId, domain, lastSequenceNumber, asList(events));
    }

    void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, List<Message> events);

    default Stream<DeserializingMessage> getDomainEvents(String aggregateId) {
        return getDomainEvents(aggregateId, -1L);
    }

    Stream<DeserializingMessage> getDomainEvents(String aggregateId, long lastSequenceNumber);

    void storeSnapshot(String aggregateId, long sequenceNumber, Object snapshot);

    <T> Optional<Snapshot<T>> getSnapshot(String aggregateId);

    void deleteSnapshot(String aggregateId);

}
