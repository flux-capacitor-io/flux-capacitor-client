package io.fluxcapacitor.javaclient.eventsourcing;

import lombok.Value;

import java.util.function.UnaryOperator;

@Value
public class EventSourcedModel<T> {
    String id;
    long sequenceNumber;
    T model;

    public EventSourcedModel<T> update(UnaryOperator<T> updateFunction) {
        return new EventSourcedModel<>(id, sequenceNumber + 1L, updateFunction.apply(model));
    }
}
