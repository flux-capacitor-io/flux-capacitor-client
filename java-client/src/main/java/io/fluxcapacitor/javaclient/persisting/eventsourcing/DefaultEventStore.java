package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.common.ConsistentHashing;
import io.fluxcapacitor.common.Registration;
import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.common.handling.Handler;
import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.persisting.eventsourcing.client.EventStoreClient;
import io.fluxcapacitor.javaclient.tracking.handling.HandlerFactory;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import static io.fluxcapacitor.common.MessageType.EVENT;
import static java.lang.String.format;

@AllArgsConstructor
public class DefaultEventStore implements EventStore {

    private final EventStoreClient client;
    private final EventStoreSerializer serializer;
    private final HandlerFactory handlerFactory;
    private final List<Handler<DeserializingMessage>> localHandlers = new CopyOnWriteArrayList<>();

    @Override
    public void storeDomainEvents(String aggregateId, String domain, long lastSequenceNumber,
                                  List<Message> events) {
        try {
            int segment = ConsistentHashing.computeSegment(aggregateId);
            List<SerializedMessage> messages = new ArrayList<>(events.size());
            events.forEach(e -> {
                SerializedMessage message = serializer.serialize(e).withSegment(segment);
                messages.add(message);
                tryHandleLocally(e.getPayload(), message);
            });
            client.storeEvents(aggregateId, domain, lastSequenceNumber, messages).await();
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

    @Override
    public Registration registerLocalHandler(Object target) {
        Optional<Handler<DeserializingMessage>> handler = handlerFactory.createHandler(target);
        handler.ifPresent(localHandlers::add);
        return () -> handler.ifPresent(localHandlers::remove);
    }

    protected void tryHandleLocally(Object payload, SerializedMessage serializedMessage) {
        if (!localHandlers.isEmpty()) {
            new DeserializingMessage(serializedMessage, () -> payload, EVENT).run(m -> {
                for (Handler<DeserializingMessage> handler : localHandlers) {
                    try {
                        if (handler.canHandle(m)) {
                            handler.invoke(m);
                        }
                    } finally {
                        handler.onEndOfBatch();
                    }
                }
            });
        }
    }
}
