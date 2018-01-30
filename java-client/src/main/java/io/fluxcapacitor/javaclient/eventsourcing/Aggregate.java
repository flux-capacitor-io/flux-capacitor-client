package io.fluxcapacitor.javaclient.eventsourcing;

import lombok.Value;

@Value
public class Aggregate<T> {
    String id;
    long sequenceNumber;
    T model;
}
