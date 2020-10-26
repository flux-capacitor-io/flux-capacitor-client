package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.Awaitable;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.handling.HasLocalHandlers;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public interface EventStore extends HasLocalHandlers {

    default Awaitable storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, Object... events) {
        return storeDomainEvents(aggregateId, domain, lastSequenceNumber, asList(events));
    }

    Awaitable storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber, List<?> events);

    default Stream<DeserializingMessage> getDomainEvents(String aggregateId) {
        return getDomainEvents(aggregateId, -1L);
    }

    Stream<DeserializingMessage> getDomainEvents(String aggregateId, long lastSequenceNumber);
}
