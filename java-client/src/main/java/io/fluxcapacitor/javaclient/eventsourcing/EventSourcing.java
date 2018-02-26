package io.fluxcapacitor.javaclient.eventsourcing;

public interface EventSourcing {

    <T> EsModel<T> newInstance(String id, Class<T> modelType);

    <T> EsModel<T> load(String id, Class<T> modelType);

    void invalidateCache();

    <T> EventSourcingRepository<T> repository(Class<T> modelClass);

    EventStore eventStore();

}
