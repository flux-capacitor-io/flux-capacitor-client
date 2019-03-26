package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.model.Model;

public interface EventSourcing {

    default <T> Model<T> load(String id, Class<T> modelType) {
        return load(id, modelType, false, false);
    }

    <T> Model<T> load(String id, Class<T> modelType, boolean disableCaching, boolean disableSnapshotting);

    void invalidateCache();

    EventStore eventStore();

}
