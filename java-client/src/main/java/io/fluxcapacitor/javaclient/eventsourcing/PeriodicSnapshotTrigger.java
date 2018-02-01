package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

import java.util.List;

public class PeriodicSnapshotTrigger implements SnapshotTrigger {
    private final int period;

    public PeriodicSnapshotTrigger(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("Period should be at least 1");
        }
        this.period = period;
    }

    @Override
    public boolean shouldCreateSnapshot(Aggregate<?> aggregate, List<Message> newEvents) {
        return periodIndex(aggregate.getSequenceNumber()) > periodIndex(aggregate.getSequenceNumber() - newEvents.size());
    }

    protected long periodIndex(long sequenceNumber) {
        return (sequenceNumber + 1) / period;
    }
}
