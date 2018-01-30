package io.fluxcapacitor.javaclient.eventsourcing;

@FunctionalInterface
public interface EventSourcingRepository<T> {

    default EsModel<T> load(String modelId) {
        return load(modelId, null);
    }

    EsModel<T> load(String modelId, Long expectedSequenceNumber);

}
