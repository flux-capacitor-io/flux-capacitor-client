package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.model.Model;

public interface EventSourcing {

    <T> Model<T> newInstance(String id, Class<T> modelType);

    <T> Model<T> load(String id, Class<T> modelType);

    void invalidateCache();

    <T> EventSourcingRepository<T> repository(Class<T> modelClass);

    EventStore eventStore();

}
