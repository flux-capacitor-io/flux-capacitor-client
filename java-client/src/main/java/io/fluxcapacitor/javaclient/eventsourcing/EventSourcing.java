package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.model.Aggregate;

public interface EventSourcing {

    default <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType) {
        return load(aggregateId, aggregateType, false, false);
    }

    <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean disableCaching, boolean disableSnapshotting);

    void invalidateCache();

    EventStore eventStore();

}
