package io.fluxcapacitor.javaclient.eventsourcing;

import java.util.Optional;

public enum NoOpSnapshotRepository implements SnapshotRepository {
    INSTANCE;

    @Override
    public void storeSnapshot(Aggregate<?> snapshot) {
        //no op
    }

    @Override
    public <T> Optional<Aggregate<T>> getSnapshot(String aggregateId) {
        return Optional.empty();
    }

    @Override
    public void deleteSnapshot(String aggregateId) {
        //no op
    }
}
