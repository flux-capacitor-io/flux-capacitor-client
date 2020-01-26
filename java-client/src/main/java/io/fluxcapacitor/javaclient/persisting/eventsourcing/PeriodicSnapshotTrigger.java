package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

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
    public boolean shouldCreateSnapshot(EventSourcedModel<?> model, List<DeserializingMessage> newEvents) {
        return periodIndex(model.sequenceNumber()) > periodIndex(model.sequenceNumber() - newEvents.size());
    }

    protected long periodIndex(long sequenceNumber) {
        return (sequenceNumber + 1) / period;
    }
}
