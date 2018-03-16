package io.fluxcapacitor.javaclient.eventsourcing;

import io.fluxcapacitor.javaclient.common.model.Model;

public interface EventSourcingRepository<T> {

    default Model<T> load(String modelId) {
        return load(modelId, null);
    }

    Model<T> load(String modelId, Long expectedSequenceNumber);

}
