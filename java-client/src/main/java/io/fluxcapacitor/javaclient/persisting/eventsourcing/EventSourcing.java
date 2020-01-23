package io.fluxcapacitor.javaclient.persisting.eventsourcing;

import io.fluxcapacitor.javaclient.modeling.Aggregate;

public interface EventSourcing {

    default <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType) {
        return load(aggregateId, aggregateType, false, false);
    }

    <T> Aggregate<T> load(String aggregateId, Class<T> aggregateType, boolean disableCaching, boolean disableSnapshotting);

    EventStore eventStore();

}
