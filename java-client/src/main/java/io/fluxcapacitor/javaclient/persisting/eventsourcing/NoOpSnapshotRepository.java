package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import java.util.Optional;

public enum NoOpSnapshotRepository implements SnapshotRepository {
    INSTANCE;

    @Override
    public void storeSnapshot(EventSourcedModel<?> snapshot) {
        //no op
    }

    @Override
    public <T> Optional<EventSourcedModel<T>> getSnapshot(String aggregateId) {
        return Optional.empty();
    }

    @Override
    public void deleteSnapshot(String aggregateId) {
        //no op
    }
}
