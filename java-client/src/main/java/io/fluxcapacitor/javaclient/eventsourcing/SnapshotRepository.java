package io.fluxcapacitor.javaclient.eventsourcing;

import java.util.Optional;

public interface SnapshotRepository {

    void storeSnapshot(EventSourcedModel<?> snapshot);

    <T> Optional<EventSourcedModel<T>> getSnapshot(String aggregateId);

    void deleteSnapshot(String aggregateId);

}
