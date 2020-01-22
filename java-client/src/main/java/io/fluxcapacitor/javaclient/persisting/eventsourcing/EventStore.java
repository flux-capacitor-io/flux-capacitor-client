package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public interface EventStore {

    default void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, Message... events) {
        storeDomainEvents(aggregateId, domain, lastSequenceNumber, asList(events));
    }

    void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, List<Message> events);

    default Stream<DeserializingMessage> getDomainEvents(String aggregateId) {
        return getDomainEvents(aggregateId, -1L);
    }

    Stream<DeserializingMessage> getDomainEvents(String aggregateId, long lastSequenceNumber);

    Registration registerLocalHandler(Object handler);

}
