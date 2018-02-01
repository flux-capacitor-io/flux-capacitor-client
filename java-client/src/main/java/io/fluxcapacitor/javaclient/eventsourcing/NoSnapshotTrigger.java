package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.Message;

import java.util.List;

public enum NoSnapshotTrigger implements SnapshotTrigger {
    INSTANCE;

    @Override
    public boolean shouldCreateSnapshot(Aggregate<?> aggregate, List<Message> newEvents) {
        return false;
    }
}
