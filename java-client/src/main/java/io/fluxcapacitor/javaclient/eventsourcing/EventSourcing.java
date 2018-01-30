package io.fluxcapacitor.javaclient.eventsourcing;

public interface EventSourcing {

    <T> EsModel<T> load(String id, Class<T> modelType);

}
