package io.fluxcapacitor.javaclient.eventsourcing;

public interface EventSourcing {

    <T> EsModel<T> load(String id, Class<T> modelType);

    <T> EventSourcingRepository<T> repository(Class<T> modelClass);

    EventStore eventStore();

}
