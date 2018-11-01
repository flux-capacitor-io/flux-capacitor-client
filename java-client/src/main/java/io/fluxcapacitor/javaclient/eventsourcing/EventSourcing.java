package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.model.Model;

public interface EventSourcing {

    <T> Model<T> load(String id, Class<T> modelType);

    void invalidateCache();

    EventStore eventStore();

}
