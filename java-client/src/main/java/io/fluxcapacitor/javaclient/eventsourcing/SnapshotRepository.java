package io.fluxcapacitor.javaclient.eventsourcing;

import java.util.Optional;

public interface SnapshotRepository {

    void storeSnapshot(Aggregate<?> snapshot);

    <T> Optional<Aggregate<T>> getSnapshot(String aggregateId);

    void deleteSnapshot(String aggregateId);

}
