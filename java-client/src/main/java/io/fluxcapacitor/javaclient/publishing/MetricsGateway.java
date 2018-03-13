package io.fluxcapacitor.javaclient.publishing;

import io.fluxcapacitor.common.api.Metadata;
import io.fluxcapacitor.javaclient.common.Message;

public interface MetricsGateway {

    default void publish(Object metrics) {
        if (metrics instanceof Message) {
            publish(((Message) metrics).getPayload(), ((Message) metrics).getMetadata());
        } else {
            publish(metrics, Metadata.empty());
        }
    }

    void publish(Object payload, Metadata metadata);

}
