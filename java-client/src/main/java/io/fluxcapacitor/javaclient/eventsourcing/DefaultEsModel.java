package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;
import io.fluxcapacitor.javaclient.common.caching.Cache;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.ArrayList;
import java.util.List;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

public class DefaultEsModel<T> implements EsModel<T> {

    private final EventSourcingHandler<T> eventSourcingHandler;
    private final Cache cache;
    private final EventStore eventStore;
    private Aggregate<T> aggregate;
    private final List<Message> unpublishedEvents = new ArrayList<>();
    private final boolean readOnly;

    public DefaultEsModel(EventSourcingHandler<T> eventSourcingHandler, Cache cache, EventStore eventStore, String id,
                          boolean readOnly) {
        this.eventSourcingHandler = eventSourcingHandler;
        this.cache = cache;
        this.eventStore = eventStore;
        this.readOnly = readOnly;
        initializeAggregate(id);
    }

    protected void initializeAggregate(String id) {
        aggregate = cache.get(id, i -> {
            Aggregate<T> result = eventStore.<T>getSnapshot(id).orElse(new Aggregate<>(id, -1L, null));
            for (DeserializingMessage event : eventStore.getDomainEvents(id, result.getSequenceNumber())
                    .collect(toList())) {
                result = new Aggregate<>(id, result.getSequenceNumber() + 1,
                                         eventSourcingHandler.apply(event.toMessage(), aggregate.getModel()));
            }
            return result;
        });
    }

    @Override
    public EsModel<T> apply(Message message) {
        if (readOnly) {
            throw new EventSourcingException(format("Not allowed to apply a %s. Model is readonly.", message));
        }
        unpublishedEvents.add(message);
        aggregate = new Aggregate<>(aggregate.getId(), aggregate.getSequenceNumber() + 1,
                                    eventSourcingHandler.apply(message, aggregate.getModel()));
        return this;
    }

    @Override
    public T get() {
        return aggregate.getModel();
    }

    public void commit() {
        if (!unpublishedEvents.isEmpty()) {
            eventStore.storeDomainEvents(aggregate.getId(), "domain", aggregate.getSequenceNumber(), unpublishedEvents);
            cache.put(aggregate.getId(), aggregate);
            unpublishedEvents.clear();
        }
    }

    public void rollback() {
        if (!unpublishedEvents.isEmpty()) {
            unpublishedEvents.clear();
            initializeAggregate(aggregate.getId());
        }
    }
}
