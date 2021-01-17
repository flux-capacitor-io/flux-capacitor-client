package io.fluxcapacitor.javaclient.publishing.correlation;

import io.fluxcapacitor.javaclient.FluxCapacitor;
import io.fluxcapacitor.javaclient.common.serialization.DeserializingMessage;
import io.fluxcapacitor.javaclient.tracking.Tracker;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;

public class DefaultCorrelationDataProvider implements CorrelationDataProvider {
    @Override
    public Map<String, String> getCorrelationData() {
        Map<String, String> result = new HashMap<>();
        FluxCapacitor.getOptionally().ifPresent(f -> {
            result.put("$clientId", f.client().id());
            result.put("$clientName", f.client().name());
        });
        ofNullable(DeserializingMessage.getCurrent()).ifPresent(currentMessage -> {
            String correlationId = ofNullable(currentMessage.getSerializedObject().getIndex())
                    .map(Object::toString).orElse(currentMessage.getSerializedObject().getMessageId());
            result.put("$correlationId", correlationId);
            result.put("$traceId", currentMessage.getMetadata().getOrDefault("$traceId", correlationId));
            result.put("$trigger", currentMessage.getSerializedObject().getData().getType());
            result.putAll(currentMessage.getMetadata().getTraceEntries());
        });
        Tracker.current().ifPresent(t -> result.put("$consumer", t.getName()));
        return result;
    }
}
