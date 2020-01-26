package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;

import java.util.List;

public enum NoSnapshotTrigger implements SnapshotTrigger {
    INSTANCE;

    @Override
    public boolean shouldCreateSnapshot(EventSourcedModel<?> model, List<DeserializingMessage> newEvents) {
        return false;
    }
}
