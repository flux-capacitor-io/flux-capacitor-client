package io.fluxcapacitor.javaclient.eventsourcing;

public interface EventSourcing {

    default <T> EsModel<T> require(String id, Class<T> modelType) {
        EsModel<T> result = load(id, modelType);
        if (result.get() == null) {
            throw new IllegalStateException(
                    String.format("Expected existing model of type %s for id %s", modelType.getSimpleName(), id));
        }
        return result;
    }

    <T> EsModel<T> load(String id, Class<T> modelType);

    <T> EventSourcingRepository<T> repository(Class<T> modelClass);

    EventStore eventStore();

}
