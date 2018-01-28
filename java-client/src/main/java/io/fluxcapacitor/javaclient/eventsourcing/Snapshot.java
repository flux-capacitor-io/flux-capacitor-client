package io.fluxcapacitor.javaclient.eventsourcing;

import lombok.Value;

@Value
public class Snapshot<T> {
    String aggregateId;
    long lastSequenceNumber;
    T aggregate;
}
