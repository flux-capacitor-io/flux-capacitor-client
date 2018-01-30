package io.fluxcapacitor.javaclient.eventsourcing;

import lombok.Value;

import java.util.function.UnaryOperator;

@Value
public class Aggregate<T> {
    String id;
    long sequenceNumber;
    T model;

    public Aggregate<T> update(UnaryOperator<T> updateFunction) {
        return new Aggregate<>(id, sequenceNumber + 1L, updateFunction.apply(model));
    }
}
