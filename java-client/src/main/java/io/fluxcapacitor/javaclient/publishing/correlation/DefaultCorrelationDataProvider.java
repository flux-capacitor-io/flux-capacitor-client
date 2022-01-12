package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.common.api.SerializedMessage;
import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.tracking.Tracker;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

public enum DefaultCorrelationDataProvider implements CorrelationDataProvider {
    INSTANCE;

    @Override
    public Map<String, String> getCorrelationData(SerializedMessage currentMessage) {
        Map<String, String> result = new HashMap<>();
        FluxCapacitor.getOptionally().ifPresent(f -> {
            result.put("$clientId", f.client().id());
            result.put("$clientName", f.client().name());
        });
        ofNullable(currentMessage).ifPresent(m -> {
            String correlationId = ofNullable(m.getIndex()).map(Object::toString).orElse(m.getMessageId());
            result.put("$correlationId", correlationId);
            result.put("$traceId", currentMessage.getMetadata().getOrDefault("$traceId", correlationId));
            result.put("$trigger", m.getData().getType());
            result.putAll(currentMessage.getMetadata().getTraceEntries());
        });
        Tracker.current().ifPresent(t -> result.put("$consumer", t.getName()));
        return result;
    }
}
